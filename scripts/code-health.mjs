import { readFile, readdir, stat } from 'node:fs/promises'
import { dirname, extname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

// Read-only inventory. Build first so the size figures describe the current source.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const label = path => relative(root, path).replaceAll('\\', '/')
async function filesIn(path) {
  const entries = await readdir(path, { withFileTypes: true }).catch(issue => {
    if (issue.code === 'ENOENT') return []
    throw issue
  })
  return (await Promise.all(entries.map(entry => {
    const pathToEntry = resolve(path, entry.name)
    return entry.isDirectory() ? filesIn(pathToEntry) : [pathToEntry]
  }))).flat()
}
async function sourceStats(directory, extensions) {
  const paths = (await filesIn(resolve(root, directory))).filter(path => extensions.includes(extname(path)))
  const files = await Promise.all(paths.map(async path => {
    const source = await readFile(path, 'utf8')
    return { path: label(path), bytes: Buffer.byteLength(source), lines: source.trimEnd().split(/\r?\n/).length }
  }))
  return {
    files: files.length,
    bytes: files.reduce((sum, file) => sum + file.bytes, 0),
    lines: files.reduce((sum, file) => sum + file.lines, 0),
    largest: files.sort((a, b) => b.bytes - a.bytes).slice(0, 8),
  }
}
const [frontend, backend, tests, migrations, distPaths, packageJson, packageLock] = await Promise.all([
  sourceStats('frontend/src', ['.vue', '.ts', '.tsx', '.css']),
  sourceStats('backend/src/main/java', ['.java']),
  sourceStats('backend/src/test', ['.java']),
  sourceStats('backend/src/main/resources/db/migration', ['.sql']),
  filesIn(resolve(root, 'frontend/dist')),
  readFile(resolve(root, 'frontend/package.json'), 'utf8').then(JSON.parse),
  readFile(resolve(root, 'frontend/package-lock.json'), 'utf8').then(JSON.parse),
])
const assets = await Promise.all(distPaths.filter(path => !path.endsWith('.map')).map(async path => {
  const bytes = await readFile(path)
  return { path: label(path), bytes: bytes.length, gzip: gzipSync(bytes).length }
}))
const scripts = assets.filter(asset => asset.path.endsWith('.js'))
const styles = assets.filter(asset => asset.path.endsWith('.css'))
const sum = (items, key) => items.reduce((total, item) => total + item[key], 0)
const publicAssets = await Promise.all((await filesIn(resolve(root, 'frontend/public'))).map(async path => ({
  path: label(path), bytes: (await stat(path)).size,
})))
const report = {
  frontend, backend, tests, migrations,
  dependencies: {
    runtime: Object.keys(packageJson.dependencies ?? {}),
    development: Object.keys(packageJson.devDependencies ?? {}),
    lockedPackages: Object.keys(packageLock.packages ?? {}).filter(path => path.startsWith('node_modules/')).length,
  },
  build: {
    // Total of every lazy route, not the transfer cost of one page visit.
    js: { files: scripts.length, bytes: sum(scripts, 'bytes'), gzip: sum(scripts, 'gzip') },
    css: { files: styles.length, bytes: sum(styles, 'bytes'), gzip: sum(styles, 'gzip') },
    largest: assets.sort((a, b) => b.bytes - a.bytes).slice(0, 10),
    publicAssets,
  },
}
if (process.argv.includes('--json')) console.log(JSON.stringify(report, null, 2))
else {
  const kb = bytes => (bytes / 1000).toFixed(1) + ' kB'
  console.log(`前端源码 ${frontend.files} 个文件，${frontend.lines} 行`)
  console.log(`后端业务 ${backend.files} 个文件，${backend.lines} 行；测试 ${tests.files} 个文件，迁移 ${migrations.files} 个`)
  console.log(`直接依赖 ${report.dependencies.runtime.length} 个，开发依赖 ${report.dependencies.development.length} 个`)
  console.log(`全部路由 JS ${kb(report.build.js.bytes)}，gzip ${kb(report.build.js.gzip)}`)
  console.log(`全部 CSS ${kb(report.build.css.bytes)}，gzip ${kb(report.build.css.gzip)}`)
  console.log('以上为全部路由产物总量。实际单页流量取决于路由、缓存、压缩配置和接口数据。')
  console.log('较大的源码文件')
  for (const file of [...frontend.largest.slice(0, 4), ...backend.largest.slice(0, 4)]) {
    console.log(`  ${file.path}  ${file.lines} 行  ${kb(file.bytes)}`)
  }
}
