/*
 * UAT-only in-memory Redis protocol subset.
 *
 * This process exists solely for local browser acceptance when no Redis service
 * is installed. It binds to loopback and implements only the commands used by
 * the auto-inbound draft/lock code. It must never be used as an application
 * runtime or production Redis replacement.
 */
const net = require('node:net')

const HOST = '127.0.0.1'
const PORT = 6379
const values = new Map()
const sortedSets = new Map()
const expiresAt = new Map()

const bulk = value => {
  const buffer = Buffer.from(String(value), 'utf8')
  return Buffer.concat([Buffer.from(`$${buffer.length}\r\n`), buffer, Buffer.from('\r\n')])
}
const simple = value => Buffer.from(`+${value}\r\n`)
const integer = value => Buffer.from(`:${Number(value)}\r\n`)
const error = value => Buffer.from(`-${value}\r\n`)
const array = items => Buffer.concat([Buffer.from(`*${items.length}\r\n`), ...items.map(bulk)])
const nullValue = protocol => Buffer.from(protocol === 3 ? '_\r\n' : '$-1\r\n')

function purgeExpired(key) {
  const deadline = expiresAt.get(key)
  if (deadline !== undefined && deadline <= Date.now()) {
    expiresAt.delete(key)
    values.delete(key)
    sortedSets.delete(key)
  }
}

function hasKey(key) {
  purgeExpired(key)
  return values.has(key) || sortedSets.has(key)
}

function parseCommand(buffer, offset = 0) {
  if (offset >= buffer.length || buffer[offset] !== 42) return null
  const arrayEnd = buffer.indexOf('\r\n', offset)
  if (arrayEnd < 0) return null
  const count = Number(buffer.subarray(offset + 1, arrayEnd).toString('ascii'))
  let cursor = arrayEnd + 2
  const args = []
  for (let index = 0; index < count; index += 1) {
    if (cursor >= buffer.length || buffer[cursor] !== 36) return null
    const lengthEnd = buffer.indexOf('\r\n', cursor)
    if (lengthEnd < 0) return null
    const length = Number(buffer.subarray(cursor + 1, lengthEnd).toString('ascii'))
    const valueStart = lengthEnd + 2
    const valueEnd = valueStart + length
    if (valueEnd + 2 > buffer.length) return null
    args.push(buffer.subarray(valueStart, valueEnd).toString('utf8'))
    cursor = valueEnd + 2
  }
  return { args, consumed: cursor }
}

function helloResponse(protocol) {
  if (protocol !== 3) {
    return array(['server', 'redis', 'version', '7.0.0-uat', 'proto', '2'])
  }
  const entries = [
    ['server', bulk('redis')],
    ['version', bulk('7.0.0-uat')],
    ['proto', integer(3)],
    ['id', integer(1)],
    ['mode', bulk('standalone')],
    ['role', bulk('master')],
    ['modules', Buffer.from('*0\r\n')]
  ]
  return Buffer.concat([
    Buffer.from(`%${entries.length}\r\n`),
    ...entries.flatMap(([key, value]) => [bulk(key), value])
  ])
}

function execute(args, state) {
  const command = String(args[0] || '').toUpperCase()
  if (command === 'PING') return args.length > 1 ? bulk(args[1]) : simple('PONG')
  if (command === 'ECHO') return bulk(args[1] || '')
  if (command === 'HELLO') {
    state.protocol = Number(args[1]) === 3 ? 3 : 2
    return helloResponse(state.protocol)
  }
  if (command === 'AUTH' || command === 'SELECT' || command === 'READONLY') return simple('OK')
  if (command === 'CLIENT') return simple('OK')
  if (command === 'COMMAND') return Buffer.from('*0\r\n')
  if (command === 'INFO') return bulk('# Server\r\nredis_version:7.0.0-uat\r\n')

  if (command === 'GET') {
    const key = args[1]
    purgeExpired(key)
    return values.has(key) ? bulk(values.get(key)) : nullValue(state.protocol)
  }
  if (command === 'SET') {
    const key = args[1]
    const value = args[2]
    let nx = false
    let ttlMs = null
    for (let index = 3; index < args.length; index += 1) {
      const option = args[index].toUpperCase()
      if (option === 'NX') nx = true
      if (option === 'EX') ttlMs = Number(args[++index]) * 1000
      if (option === 'PX') ttlMs = Number(args[++index])
    }
    if (nx && hasKey(key)) return nullValue(state.protocol)
    values.set(key, value)
    sortedSets.delete(key)
    if (ttlMs !== null) expiresAt.set(key, Date.now() + ttlMs)
    else expiresAt.delete(key)
    return simple('OK')
  }
  if (command === 'SETEX' || command === 'PSETEX') {
    const key = args[1]
    const multiplier = command === 'SETEX' ? 1000 : 1
    values.set(key, args[3])
    sortedSets.delete(key)
    expiresAt.set(key, Date.now() + Number(args[2]) * multiplier)
    return simple('OK')
  }
  if (command === 'DEL') {
    let removed = 0
    for (const key of args.slice(1)) {
      if (hasKey(key)) removed += 1
      values.delete(key)
      sortedSets.delete(key)
      expiresAt.delete(key)
    }
    return integer(removed)
  }
  if (command === 'EXPIRE' || command === 'PEXPIRE') {
    const key = args[1]
    if (!hasKey(key)) return integer(0)
    const multiplier = command === 'EXPIRE' ? 1000 : 1
    expiresAt.set(key, Date.now() + Number(args[2]) * multiplier)
    return integer(1)
  }
  if (command === 'ZADD') {
    const key = args[1]
    purgeExpired(key)
    const set = sortedSets.get(key) || new Map()
    let created = 0
    for (let index = 2; index + 1 < args.length; index += 2) {
      const score = Number(args[index])
      const member = args[index + 1]
      if (!set.has(member)) created += 1
      set.set(member, score)
    }
    sortedSets.set(key, set)
    values.delete(key)
    return integer(created)
  }
  if (command === 'ZSCORE') {
    const key = args[1]
    purgeExpired(key)
    const score = sortedSets.get(key)?.get(args[2])
    return score === undefined ? nullValue(state.protocol) : bulk(score)
  }
  if (command === 'ZREM') {
    const key = args[1]
    purgeExpired(key)
    const set = sortedSets.get(key)
    if (!set) return integer(0)
    let removed = 0
    for (const member of args.slice(2)) {
      if (set.delete(member)) removed += 1
    }
    return integer(removed)
  }
  if (command === 'ZREVRANGE' || command === 'ZRANGE') {
    const key = args[1]
    purgeExpired(key)
    const reverse = command === 'ZREVRANGE'
    const start = Number(args[2])
    const stop = Number(args[3])
    const withScores = args.slice(4).some(value => value.toUpperCase() === 'WITHSCORES')
    const entries = [...(sortedSets.get(key) || new Map()).entries()]
      .sort((a, b) => reverse ? b[1] - a[1] : a[1] - b[1])
    const end = stop < 0 ? entries.length : stop + 1
    const selected = entries.slice(start, end)
    const response = []
    for (const [member, score] of selected) {
      response.push(member)
      if (withScores) response.push(String(score))
    }
    return array(response)
  }
  if (command === 'EVALSHA') {
    return error('NOSCRIPT No matching script. Please use EVAL.')
  }
  if (command === 'EVAL') {
    const keyCount = Number(args[2])
    const key = args[3]
    const token = args[3 + keyCount]
    purgeExpired(key)
    if (values.get(key) === token) {
      values.delete(key)
      expiresAt.delete(key)
      return integer(1)
    }
    return integer(0)
  }
  return error(`ERR unknown command '${args[0] || ''}' in UAT Redis subset`)
}

const server = net.createServer(socket => {
  let pending = Buffer.alloc(0)
  const state = { protocol: 2 }
  socket.on('data', chunk => {
    pending = Buffer.concat([pending, chunk])
    while (pending.length) {
      const parsed = parseCommand(pending)
      if (!parsed) break
      pending = pending.subarray(parsed.consumed)
      try {
        socket.write(execute(parsed.args, state))
      } catch (cause) {
        socket.write(error(`ERR ${cause instanceof Error ? cause.message : 'UAT server failure'}`))
      }
    }
  })
})

server.listen(PORT, HOST, () => {
  process.stdout.write(`UAT_MINIMAL_REDIS_READY ${HOST}:${PORT}\n`)
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)))
}
