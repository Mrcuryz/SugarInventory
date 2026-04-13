const tableCellSelector = '.el-table .cell'

const isOverflowed = (element) => {
    return element.scrollWidth > element.clientWidth || element.scrollHeight > element.clientHeight
}

const normalizeText = (text) => text.replace(/\s+/g, ' ').trim()

const handleTableCellMouseover = (event) => {
    const target = event.target
    if (!(target instanceof Element)) {
        return
    }

    const cell = target.closest(tableCellSelector)
    if (!cell) {
        return
    }

    const text = normalizeText(cell.innerText || cell.textContent || '')
    if (!text) {
        return
    }

    const overflowed = isOverflowed(cell) || Array.from(cell.children).some(isOverflowed)
    if (overflowed) {
        cell.dataset.autoTableTitle = 'true'
        cell.setAttribute('title', text)
        return
    }

    if (cell.dataset.autoTableTitle === 'true') {
        cell.removeAttribute('title')
        delete cell.dataset.autoTableTitle
    }
}

export const setupTableOverflowTitle = () => {
    document.addEventListener('mouseover', handleTableCellMouseover, true)
}
