const validId = value => Number.isSafeInteger(Number(value)) && Number(value) > 0

export function buildEquipmentCatalog(rows = []) {
	const enabled = rows.filter(item => item && item.enabled !== false && validId(item.id) && item.code && item.name)
	const roots = enabled.filter(item => item.parentId == null)
	// This is the existing equipment group, distinct from the older air-conditioning-only group.
	const root = roots.find(item => item.code === 'lczz-equipment') || roots.find(item => String(item.name).trim() === '设备') || null
	const children = root ? enabled.filter(item => Number(item.parentId) === Number(root.id))
		.sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0) || Number(a.id) - Number(b.id)) : []
	return {
		root,
		tabs: [{ name: '全部', type: 'all' }, ...children.map(item => ({ name: item.name, type: item.code, id: item.id, parentId: item.parentId }))]
	}
}

export function equipmentQuery(root, tab, keyword, page = 1, pageSize = 6) {
	if (!root || root.enabled === false || !validId(root.id) || !root.code || !tab) return null
	if (tab.type !== 'all' && (!validId(tab.id) || Number(tab.parentId) !== Number(root.id))) return null
	return {
		page,
		pageSize,
		categoryRootId: Number(root.id),
		// Keep the category code as well, so the equipment scope is explicit in every request.
		category: tab.type === 'all' ? root.code : tab.type,
		...(tab.type === 'all' ? {} : { categoryId: Number(tab.id) }),
		...(String(keyword || '').trim() ? { keyword: String(keyword).trim() } : {})
	}
}
