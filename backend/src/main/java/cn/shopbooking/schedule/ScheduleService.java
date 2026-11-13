package cn.shopbooking.schedule;

import cn.shopbooking.shop.ShopService;
import cn.shopbooking.web.ApiException;
import cn.shopbooking.web.Inputs;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {
    public record Member(long id, String name, boolean active) {}
    public record Shift(int weekday, int startMinute, int endMinute) {}
    public record Employee(long id, String name, boolean active, List<Long> serviceIds,
                           List<Shift> shifts, List<LocalDate> closedDates) {}
    public record EmployeeInput(String name, Boolean active, List<Long> serviceIds,
                                List<Shift> shifts, List<LocalDate> closedDates) {}

    private final JdbcClient db;
    private final ShopService shops;

    public ScheduleService(JdbcClient db, ShopService shops) {
        this.db = db;
        this.shops = shops;
    }

    public Member member(long shopId, long id) {
        return db.sql("select id, name, active from employee where shop_id = ? and id = ?")
                .params(shopId, id).query(Member.class).optional()
                .orElseThrow(() -> ApiException.missing("员工不存在。"));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Employee> employees(long shopId) {
        return db.sql("select id, name, active from employee where shop_id = ? order by id")
                .param(shopId).query(Member.class).list().stream().map(this::details).toList();
    }

    public List<Member> eligible(long shopId, long serviceId) {
        return db.sql("""
                select e.id, e.name, e.active from employee e
                join employee_service es on es.employee_id = e.id
                where e.shop_id = ? and es.service_id = ? and e.active order by e.id
                """).params(shopId, serviceId).query(Member.class).list();
    }

    public List<Shift> shiftsFor(long employeeId, LocalDate day) {
        return db.sql("""
                select weekday, start_minute, end_minute from weekly_shift
                where employee_id = :employee and weekday = :weekday and not exists (
                    select 1 from closed_date where employee_id = :employee and closed_on = :day
                ) order by start_minute
                """).param("employee", employeeId).param("weekday", day.getDayOfWeek().getValue())
                .param("day", day).query(Shift.class).list();
    }

    public boolean readyToPublish(long shopId) {
        var offerings = shops.offerings(shopId);
        return employees(shopId).stream().anyMatch(employee -> employee.active()
                && offerings.stream().anyMatch(service -> service.active() && employee.serviceIds().contains(service.id())
                && employee.shifts().stream().anyMatch(shift -> shift.endMinute() - shift.startMinute()
                        >= service.durationMinutes())));
    }

    @Transactional
    public Employee save(long shopId, Long employeeId, EmployeeInput input) {
        String name = Inputs.required(input.name(), 60, "员工姓名");
        if (input.active() == null || input.serviceIds() == null || input.shifts() == null
                || input.closedDates() == null || input.shifts().size() > 56
                || input.closedDates().size() > 366 || input.serviceIds().size() > 1000
                || input.shifts().contains(null) || input.closedDates().contains(null)
                || input.serviceIds().contains(null)) {
            throw ApiException.invalid("请检查员工排班、服务或停约日期。");
        }
        List<Shift> shifts = input.shifts().stream()
                .sorted(Comparator.comparingInt(Shift::weekday).thenComparingInt(Shift::startMinute)).toList();
        validateShifts(shifts);
        shops.lockConfiguration(shopId);
        var shopServices = shops.offerings(shopId).stream().map(ShopService.Offering::id).toList();
        var serviceIds = new HashSet<>(input.serviceIds());
        if (!shopServices.containsAll(serviceIds)) {
            throw ApiException.invalid("请为员工选择本店的服务。");
        }
        long id;
        if (employeeId == null) {
            id = db.sql("insert into employee(shop_id, name, active) values (?, ?, ?) returning id")
                    .params(shopId, name, input.active()).query(Long.class).single();
        } else {
            id = member(shopId, employeeId).id();
            db.sql("update employee set name = ?, active = ? where shop_id = ? and id = ?")
                    .params(name, input.active(), shopId, id).update();
            db.sql("delete from employee_service where employee_id = ?").param(id).update();
            db.sql("delete from weekly_shift where employee_id = ?").param(id).update();
            db.sql("delete from closed_date where employee_id = ?").param(id).update();
        }
        for (long serviceId : serviceIds) {
            db.sql("insert into employee_service(shop_id, employee_id, service_id) values (?, ?, ?)")
                    .params(shopId, id, serviceId).update();
        }
        for (Shift shift : shifts) {
            db.sql("insert into weekly_shift(employee_id, weekday, start_minute, end_minute) values (?, ?, ?, ?)")
                    .params(id, shift.weekday(), shift.startMinute(), shift.endMinute()).update();
        }
        for (LocalDate day : new HashSet<>(input.closedDates())) {
            db.sql("insert into closed_date(employee_id, closed_on) values (?, ?)").params(id, day).update();
        }
        return details(new Member(id, name, input.active()));
    }

    private Employee details(Member member) {
        List<Long> serviceIds = db.sql("select service_id from employee_service where employee_id = ? order by service_id")
                .param(member.id()).query(Long.class).list();
        List<Shift> shifts = db.sql("""
                select weekday, start_minute, end_minute from weekly_shift
                where employee_id = ? order by weekday, start_minute
                """).param(member.id()).query(Shift.class).list();
        List<LocalDate> dates = db.sql("select closed_on from closed_date where employee_id = ? order by closed_on")
                .param(member.id()).query(LocalDate.class).list();
        return new Employee(member.id(), member.name(), member.active(), serviceIds, shifts, dates);
    }

    private static void validateShifts(List<Shift> shifts) {
        Shift previous = null;
        for (Shift shift : shifts) {
            if (shift.weekday() < 1 || shift.weekday() > 7 || shift.startMinute() < 0 || shift.endMinute() > 1440
                    || shift.startMinute() >= shift.endMinute() || shift.startMinute() % 15 != 0
                    || shift.endMinute() % 15 != 0
                    || (previous != null && previous.weekday() == shift.weekday()
                    && previous.endMinute() > shift.startMinute())) {
                throw ApiException.invalid("排班须在当天，按 15 分钟设置，且不能重叠。");
            }
            previous = shift;
        }
    }
}
