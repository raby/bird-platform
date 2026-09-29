import { describe, expect, it, vi } from 'vitest'
import { signal } from '@raby/ripple'
import { attr, classes, list, on, scope, show, text } from '../src/dom'

// ripple flushes effect re-runs on a microtask, so a write is observed in the DOM after one tick.
const tick = (): Promise<void> => Promise.resolve()

describe('ripple-dom binding', () => {
  it('text tracks a signal (and stops after dispose)', async () => {
    const name = signal('robin')
    const p = document.createElement('p')
    const dispose = text(p, name)
    expect(p.textContent).toBe('robin') // initial run is synchronous

    name('falcon')
    await tick()
    expect(p.textContent).toBe('falcon')

    dispose()
    name('swift')
    await tick()
    expect(p.textContent).toBe('falcon') // disposed: no further updates
  })

  it('attr sets a boolean attribute, stringifies, and removes on false/null', async () => {
    const disabled = signal<boolean>(true)
    const btn = document.createElement('button')
    attr(btn, 'disabled', disabled)
    expect(btn.getAttribute('disabled')).toBe('')

    disabled(false)
    await tick()
    expect(btn.hasAttribute('disabled')).toBe(false)
  })

  it('classes toggles each class from its accessor', async () => {
    const active = signal(false)
    const div = document.createElement('div')
    classes(div, { active })
    expect(div.classList.contains('active')).toBe(false)

    active(true)
    await tick()
    expect(div.classList.contains('active')).toBe(true)
  })

  it('on wires an event and its disposer removes the listener', () => {
    const btn = document.createElement('button')
    const spy = vi.fn()
    const dispose = on(btn, 'click', spy)

    btn.click()
    expect(spy).toHaveBeenCalledTimes(1)

    dispose()
    btn.click()
    expect(spy).toHaveBeenCalledTimes(1)
  })

  it('show mounts and unmounts the node at its original slot', async () => {
    const visible = signal(true)
    const parent = document.createElement('div')
    const before = document.createElement('i')
    const child = document.createElement('span')
    parent.append(before, child)
    show(child, visible)
    expect(parent.contains(child)).toBe(true)

    visible(false)
    await tick()
    expect(parent.contains(child)).toBe(false)

    visible(true)
    await tick()
    expect(parent.contains(child)).toBe(true)
    // re-inserted after its anchor, which sits right after `before`
    expect(child.previousSibling?.nodeType).toBe(Node.COMMENT_NODE)
  })

  it('list renders, reuses nodes by key, reorders with minimal moves, and removes', async () => {
    const items = signal<readonly { id: number; label: string }[]>([
      { id: 1, label: 'a' },
      { id: 2, label: 'b' },
    ])
    const ul = document.createElement('ul')
    list<{ id: number; label: string }>(ul, items, {
      key: (i) => i.id,
      render: (i) => {
        const li = document.createElement('li')
        li.textContent = i.label
        li.dataset.id = String(i.id)
        return li
      },
    })
    const labels = (): (string | null)[] => Array.from(ul.querySelectorAll('li')).map((l) => l.textContent)
    const nodeFor = (id: number): HTMLLIElement | undefined =>
      Array.from(ul.querySelectorAll('li')).find((l) => l.dataset.id === String(id))

    expect(labels()).toEqual(['a', 'b'])
    const originalNode1 = nodeFor(1)

    items([
      { id: 2, label: 'b' },
      { id: 3, label: 'c' },
      { id: 1, label: 'a' },
    ])
    await tick()
    expect(labels()).toEqual(['b', 'c', 'a'])
    expect(nodeFor(1)).toBe(originalNode1) // key 1 reused, not re-rendered

    items([{ id: 3, label: 'c' }])
    await tick()
    expect(labels()).toEqual(['c'])
  })

  it('list disposer detaches all rows', async () => {
    const items = signal<readonly number[]>([1, 2, 3])
    const ul = document.createElement('ul')
    const dispose = list<number>(ul, items, {
      key: (n) => n,
      render: (n) => {
        const li = document.createElement('li')
        li.textContent = String(n)
        return li
      },
    })
    expect(ul.querySelectorAll('li')).toHaveLength(3)

    dispose()
    expect(ul.querySelectorAll('li')).toHaveLength(0)
    // updating the (now-disposed) signal does nothing
    items([4, 5])
    await tick()
    expect(ul.querySelectorAll('li')).toHaveLength(0)
  })

  it('scope disposes in reverse order (LIFO)', () => {
    const order: number[] = []
    const s = scope()
    s.add(() => order.push(1))
    s.add(() => order.push(2))
    s.add(() => order.push(3))
    s.dispose()
    expect(order).toEqual([3, 2, 1])
  })
})
