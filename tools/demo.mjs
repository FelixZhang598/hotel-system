import { spawn, spawnSync } from "node:child_process";
import {
  mkdirSync,
  existsSync,
  readFileSync,
  writeFileSync,
  openSync,
  closeSync,
  unlinkSync,
  readdirSync,
} from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve, dirname } from "node:path";
import net from "node:net";
const root = resolve(dirname(fileURLToPath(import.meta.url)), ".."),
  runtime = resolve(root, ".runtime"),
  data = resolve(root, "data");
mkdirSync(runtime, { recursive: true });
mkdirSync(data, { recursive: true });
const pidfile = resolve(runtime, "demo-processes.json"),
  lockfile = resolve(runtime, "demo-control.lock");
const alive = (pid) => {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
};
let lock;
try {
  lock = openSync(lockfile, "wx");
  writeFileSync(lock, String(process.pid));
} catch {
  console.error(
    "另一个启动/停止/重置操作正在进行。若此前进程被强制终止，请确认锁文件中的 PID 已退出，再删除 .runtime/demo-control.lock。",
  );
  process.exit(1);
}
let cleaned = false;
function releaseLock() {
  if (!cleaned) {
    cleaned = true;
    closeSync(lock);
    if (existsSync(lockfile)) unlinkSync(lockfile);
  }
}
for (const signal of ["SIGINT", "SIGTERM"])
  process.once(signal, () => {
    releaseLock();
    process.exit(signal === "SIGINT" ? 130 : 143);
  });
function records() {
  return existsSync(pidfile) ? JSON.parse(readFileSync(pidfile, "utf8")) : [];
}
function owned(record) {
  if (!alive(record.pid)) return false;
  const ps = spawnSync("ps", ["-p", String(record.pid), "-o", "command="], {
    encoding: "utf8",
  });
  return ps.status === 0 && ps.stdout.includes(record.marker);
}
function run(cmd, args, cwd = root) {
  const r = spawnSync(cmd, args, { cwd, stdio: "inherit" });
  if (r.status !== 0) throw Error(`${cmd} failed (${r.status})`);
}
async function portFree(port) {
  return new Promise((resolve) => {
    const s = net.createServer();
    s.once("error", () => resolve(false));
    s.listen(port, "127.0.0.1", () => s.close(() => resolve(true)));
  });
}
async function waitReady(url, ms = 60000) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    try {
      const r = await fetch(url, { signal: AbortSignal.timeout(1500) });
      if (r.ok) return;
    } catch {}
    await new Promise((r) => setTimeout(r, 400));
  }
  throw Error(`服务未就绪：${url}，请查看 .runtime/*.log`);
}
function launch(cmd, args, cwd, log, marker, env = {}) {
  const fd = openSync(resolve(runtime, log), "a");
  const child = spawn(cmd, args, {
    cwd,
    env: { ...process.env, ...env },
    detached: true,
    stdio: ["ignore", fd, fd],
  });
  closeSync(fd);
  child.unref();
  if (!child.pid) throw Error(`无法启动 ${cmd}`);
  return { pid: child.pid, marker };
}
async function stop() {
  const list = records();
  for (const r of list) {
    if (owned(r)) process.kill(r.pid, "SIGTERM");
    else if (alive(r.pid)) throw Error(`PID ${r.pid} 不属于本项目，拒绝停止。`);
  }
  const end = Date.now() + 10000;
  while (list.some(owned) && Date.now() < end)
    await new Promise((r) => setTimeout(r, 200));
  if (list.some(owned)) throw Error("进程尚未停止，请检查日志后重试。");
  if (existsSync(pidfile)) unlinkSync(pidfile);
}
try {
  const action = process.argv[2];
  if (action === "start") {
    if (records().length === 2 && records().every(owned)) {
      await waitReady("http://127.0.0.1:8080/api/meta", 5000);
      await waitReady("http://127.0.0.1:5173", 5000);
      console.log("演示实例已经启动：http://127.0.0.1:5173");
    } else {
      if (records().length) await stop();
      for (const p of [8080, 5173])
        if (!(await portFree(p)))
          throw Error(`端口 ${p} 已被占用；未操作其他进程。`);
      const java = spawnSync("java", ["-version"], { encoding: "utf8" });
      if (
        java.status !== 0 ||
        !/(?:version|openjdk) "?26[.\s"]/i.test(java.stderr + java.stdout)
      )
        throw Error("需要 Java 26，请先配置 java 命令。");
      run(
        "./mvnw",
        ["-Dmaven.test.skip=true", "package"],
        resolve(root, "backend"),
      );
      if (!existsSync(resolve(root, "frontend/node_modules")))
        run("npm", ["ci"], resolve(root, "frontend"));
      run("npm", ["run", "build"], resolve(root, "frontend"));
      const jarName = readdirSync(resolve(root, "backend/target")).find(
        (x) => x.endsWith(".jar") && !x.endsWith("-sources.jar"),
      );
      if (!jarName) throw Error("未找到后端 jar");
      const jar = resolve(root, "backend/target", jarName),
        vite = resolve(root, "frontend/node_modules/vite/bin/vite.js");
      const list = [];
      try {
        list.push(
          launch(
            "java",
            ["--enable-native-access=ALL-UNNAMED", "-jar", jar],
            resolve(root, "backend"),
            "backend.log",
            jar,
            { HOTEL_DB: resolve(data, "hotel.db"), SERVER_PORT: "8080" },
          ),
        );
        writeFileSync(pidfile, JSON.stringify(list));
        await waitReady("http://127.0.0.1:8080/api/meta");
        list.push(
          launch(
            process.execPath,
            [vite, "--host", "127.0.0.1", "--port", "5173", "--strictPort"],
            resolve(root, "frontend"),
            "frontend.log",
            vite,
            { API_TARGET: "http://127.0.0.1:8080" },
          ),
        );
        writeFileSync(pidfile, JSON.stringify(list));
        await waitReady("http://127.0.0.1:5173");
        console.log(
          "酒店演示已启动：http://127.0.0.1:5173\n住客 guest1 / guest2；前台 frontdesk；密码均为 Demo12345!",
        );
      } catch (e) {
        await stop();
        throw e;
      }
    }
  } else if (action === "stop") {
    await stop();
    console.log("本项目演示进程已停止。");
  } else if (action === "reset") {
    if (records().some(owned) || !(await portFree(8080)))
      throw Error("请先停止后端再重置数据。");
    const files = ["hotel.db", "hotel.db-wal", "hotel.db-shm"]
      .map((n) => resolve(data, n))
      .filter(existsSync);
    if (files.length) {
      const opened = spawnSync("lsof", ["-t", "--", ...files], {
        encoding: "utf8",
      });
      if (opened.error || ![0, 1].includes(opened.status))
        throw Error("无法通过 lsof 确认数据库已关闭，拒绝重置。");
      if (opened.stdout.trim())
        throw Error("数据库仍被进程打开，请先停止所有使用此数据库的实例。");
    }
    for (const name of ["hotel.db", "hotel.db-wal", "hotel.db-shm"]) {
      const p = resolve(data, name);
      if (existsSync(p)) unlinkSync(p);
    }
    console.log("演示数据库已重置，下次启动会生成相对当天的数据。");
  } else throw Error("Usage: node tools/demo.mjs start|stop|reset");
} catch (e) {
  console.error(e.message);
  process.exitCode = 1;
} finally {
  releaseLock();
}
