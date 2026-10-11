import net from 'node:net'

/** 清空 e2e 用的 Redis 5 号库：上一次运行留下的授权缓存版本号与新库的用户 id 可能撞上。 */
export default async function globalSetup() {
  const host = process.env.E2E_REDIS_HOST ?? 'localhost'
  const port = Number(process.env.E2E_REDIS_PORT ?? 16379)
  await new Promise<void>((resolve, reject) => {
    const sock = net.connect(port, host, () => {
      sock.write('*2\r\n$6\r\nSELECT\r\n$1\r\n5\r\n*1\r\n$7\r\nFLUSHDB\r\n')
    })
    let buf = ''
    sock.on('data', (d) => {
      buf += d.toString()
      if ((buf.match(/\+OK/g) ?? []).length >= 2) {
        sock.end()
        resolve()
      }
    })
    sock.on('error', reject)
    setTimeout(() => reject(new Error('Redis 无响应')), 5000)
  })
}
