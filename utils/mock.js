/**
 * Mock 数据层（纯数据 + 辅助函数）
 * ─ 所有数据字段已与页面模板直接对齐，页面拿到 res.data 即可直接渲染，无需 mapXxx 转换
 * ─ api.js 每个接口在 isMockMode() 时通过 mockSuccess/mockPaging 返回本文件数据
 * ─ 切换真实后端时：把 config.js 的 USE_MOCK_LOGIN 改为 false，页面代码无需任何修改
 *    （只需保证后端返回的 { code, data, msg } 中 data 字段名与本文件一致即可）
 * ─ 数据量已保证每个分类/状态都能体现分页（耗材每分类8条、订单每状态8条、资讯14条）
 */

// 分类英文 → 中文映射（耗材 type 字段为英文，category 为中文，供工具弹窗分类标签展示）
const _categoryMap = {
	copper: '铜管类',
	bracket: '支架类',
	cable: '电缆线类',
	refrigerant: '冷媒类',
	aux: '辅材类'
}

// ====================== 耗材（供 index 列表 / goos-details 详情 / order-detail 工具弹窗） ======================
// 字段已与三处页面模板直接对齐，res.data 即可渲染，无需 mapXxx：
//  - index 列表用 title/image/price/sales/tag/tagColor/type
//  - goos-details 详情用 title/image/tag/price/oldPrice/sales/stock/desc/tags/detailImages
//  - order-detail 工具弹窗用 id/title/spec/price/image/category
const _consumableBase = [
	{ id: 1, title: '纯铜连接管', image: 'https://picsum.photos/200/200?random=502', price: 35, oldPrice: 46, sales: '8000+', stock: 120, tag: '热销', tagColor: '#ff4d4f', type: 'copper', category: '铜管类', desc: '3米~4米可定制，纯铜材质，导冷效果好', tags: ['正品保证', '极速发货'], detailImages: ['https://picsum.photos/750/500?random=601', 'https://picsum.photos/750/500?random=602'], spec: '3米~4米可定制', model: 'GT-3M' },
	{ id: 2, title: '不锈钢支架', image: 'https://picsum.photos/200/200?random=503', price: 28, oldPrice: 36, sales: '5000+', stock: 80, tag: '', tagColor: '', type: 'bracket', category: '支架类', desc: '通用型承重50kg，加厚不锈钢', tags: ['正品保证'], detailImages: ['https://picsum.photos/750/500?random=603'], spec: '通用型承重50kg', model: 'ZJ-50' },
	{ id: 3, title: '阻燃电缆线', image: 'https://picsum.photos/200/200?random=504', price: 22, oldPrice: 29, sales: '3000+', stock: 200, tag: '', tagColor: '', type: 'cable', category: '电缆线类', desc: '3米 2.5平方，国标阻燃', tags: ['正品保证'], detailImages: ['https://picsum.photos/750/500?random=604'], spec: '3米 2.5平方', model: 'DL-25' },
	{ id: 4, title: 'R410a环保冷媒', image: 'https://picsum.photos/200/200?random=505', price: 65, oldPrice: 85, sales: '4000+', stock: 50, tag: '新品', tagColor: '#07c160', type: 'refrigerant', category: '冷媒类', desc: '1kg装，环保冷媒，适用变频空调', tags: ['正品保证', '环保'], detailImages: ['https://picsum.photos/750/500?random=605'], spec: '1kg装', model: 'R410a-1' },
	{ id: 5, title: '排水管', image: 'https://picsum.photos/200/200?random=506', price: 12, oldPrice: 16, sales: '9000+', stock: 300, tag: '', tagColor: '', type: 'aux', category: '辅材类', desc: '3米 16mm，耐腐蚀排水管', tags: ['正品保证'], detailImages: ['https://picsum.photos/750/500?random=606'], spec: '3米 16mm', model: 'PS-16' },
	{ id: 6, title: '铜连接管', image: 'https://picsum.photos/200/200?random=507', price: 38, oldPrice: 49, sales: '7000+', stock: 90, tag: '', tagColor: '', type: 'copper', category: '铜管类', desc: '3米~4米，纯铜连接管', tags: ['正品保证'], detailImages: ['https://picsum.photos/750/500?random=607'], spec: '3米~4米', model: 'GT-34' },
	{ id: 7, title: '空调连接线', image: 'https://picsum.photos/200/200?random=508', price: 18, oldPrice: 23, sales: '3500+', stock: 150, tag: '', tagColor: '', type: 'cable', category: '电缆线类', desc: '3米 国标，纯铜连接线', tags: ['正品保证'], detailImages: ['https://picsum.photos/750/500?random=608'], spec: '3米 国标', model: 'DL-GB' },
	{ id: 8, title: '万能遥控器', image: 'https://picsum.photos/200/200?random=509', price: 25, oldPrice: 32, sales: '6000+', stock: 60, tag: '', tagColor: '', type: 'aux', category: '辅材类', desc: '通用型适配多品牌', tags: ['正品保证'], detailImages: ['https://picsum.photos/750/500?random=609'], spec: '通用型适配多品牌', model: 'YK-ALL' },
	{ id: 9, title: '加厚不锈钢支架', image: 'https://picsum.photos/200/200?random=510', price: 45, oldPrice: 58, sales: '2000+', stock: 40, tag: '', tagColor: '', type: 'bracket', category: '支架类', desc: '承重80kg，加厚不锈钢', tags: ['正品保证'], detailImages: ['https://picsum.photos/750/500?random=610'], spec: '承重80kg', model: 'ZJ-80' },
	{ id: 10, title: 'R32环保冷媒', image: 'https://picsum.photos/200/200?random=511', price: 48, oldPrice: 62, sales: '2500+', stock: 35, tag: '', tagColor: '', type: 'refrigerant', category: '冷媒类', desc: '500g装，环保冷媒', tags: ['正品保证'], detailImages: ['https://picsum.photos/750/500?random=611'], spec: '500g装', model: 'R32-05' },
	{ id: 11, title: '保温棉管', image: 'https://picsum.photos/200/200?random=512', price: 15, oldPrice: 19, sales: '4500+', stock: 180, tag: '', tagColor: '', type: 'aux', category: '辅材类', desc: '9mm厚度 3米，保温棉管', tags: ['正品保证'], detailImages: ['https://picsum.photos/750/500?random=612'], spec: '9mm厚度 3米', model: 'BW-9' },
	{ id: 12, title: '管大师移动空调排风管', image: 'https://picsum.photos/200/200?random=501', price: 56, oldPrice: 72, sales: '6000+', stock: 25, tag: '热销', tagColor: '#ff4d4f', type: 'aux', category: '辅材类', desc: '管内径15.2cm 拉直五米', tags: ['正品保证', '热销'], detailImages: ['https://picsum.photos/750/500?random=613'], spec: '管内径15.2cm 拉直五米', model: 'PG-152' }
]

// 耗材扩充模板（按分类补足，确保每个分类过滤后仍能体现分页）
const _consumableExtra = [
	// 铜管类 +6
	{ title: '加厚纯铜连接管', type: 'copper', price: 42, oldPrice: 55, stock: 75, spec: '4米 纯铜加厚', model: 'GT-4M', desc: '4米加厚纯铜连接管，耐腐蚀导冷佳' },
	{ title: '空调专用铜管', type: 'copper', price: 30, oldPrice: 39, stock: 130, spec: '3米 6mm', model: 'GT-6', desc: '空调专用纯铜管，6mm口径' },
	{ title: '螺纹纯铜管', type: 'copper', price: 52, oldPrice: 68, stock: 60, spec: '5米 螺纹', model: 'GT-5L', desc: '5米螺纹纯铜管，连接牢固不漏冷' },
	{ title: '薄壁铜连接管', type: 'copper', price: 26, oldPrice: 33, stock: 95, spec: '3米 薄壁', model: 'GT-3B', desc: '3米薄壁铜管，轻便易安装' },
	{ title: '高纯度铜管', type: 'copper', price: 22, oldPrice: 28, stock: 110, spec: '2米 高纯度', model: 'GT-2H', desc: '2米高纯度铜管，导冷效率高' },
	{ title: '加长纯铜管', type: 'copper', price: 58, oldPrice: 75, stock: 45, spec: '6米 加长', model: 'GT-6M', desc: '6米加长纯铜管，大户型适用' },
	// 支架类 +6
	{ title: '加重不锈钢支架', type: 'bracket', price: 55, oldPrice: 72, stock: 50, spec: '承重100kg', model: 'ZJ-100', desc: '承重100kg，加厚不锈钢支架' },
	{ title: '镀锌空调支架', type: 'bracket', price: 35, oldPrice: 45, stock: 85, spec: '承重60kg 镀锌', model: 'ZJ-60D', desc: '镀锌防锈处理，承重60kg' },
	{ title: '可调节空调支架', type: 'bracket', price: 48, oldPrice: 62, stock: 70, spec: '可调节 50-80cm', model: 'ZJ-TJ', desc: '长度可调，适配多种机型' },
	{ title: '落地式柜机支架', type: 'bracket', price: 65, oldPrice: 85, stock: 30, spec: '落地式', model: 'ZJ-LD', desc: '落地式支架，柜机专用' },
	{ title: 'L型不锈钢支架', type: 'bracket', price: 32, oldPrice: 42, stock: 90, spec: 'L型 承重50kg', model: 'ZJ-LX', desc: 'L型设计，安装简便' },
	{ title: '加宽空调支架', type: 'bracket', price: 42, oldPrice: 55, stock: 65, spec: '加宽 承重70kg', model: 'ZJ-KW', desc: '加宽底座，承重70kg更稳固' },
	// 电缆线类 +6
	{ title: '国标电源线', type: 'cable', price: 20, oldPrice: 26, stock: 160, spec: '3米 1.5平方', model: 'DL-15', desc: '国标电源线，3米1.5平方纯铜' },
	{ title: '空调专用电缆', type: 'cable', price: 28, oldPrice: 36, stock: 120, spec: '4米 4平方', model: 'DL-4M', desc: '空调专用电缆，4米4平方' },
	{ title: '阻燃护套线', type: 'cable', price: 24, oldPrice: 31, stock: 140, spec: '3米 阻燃', model: 'DL-ZR', desc: '阻燃护套线，安全可靠' },
	{ title: '纯铜电源插头线', type: 'cable', price: 16, oldPrice: 21, stock: 200, spec: '2米 16A', model: 'DL-16A', desc: '16A大功率插头线' },
	{ title: '大功率空调线', type: 'cable', price: 35, oldPrice: 46, stock: 100, spec: '5米 6平方', model: 'DL-6M', desc: '5米6平方大功率空调线' },
	{ title: '信号控制线', type: 'cable', price: 14, oldPrice: 18, stock: 220, spec: '3米 信号线', model: 'DL-XH', desc: '空调信号控制线，3米' },
	// 冷媒类 +6
	{ title: 'R22制冷剂', type: 'refrigerant', price: 38, oldPrice: 50, stock: 45, spec: '1kg装', model: 'R22-1', desc: 'R22制冷剂，1kg装' },
	{ title: 'R410a冷媒', type: 'refrigerant', price: 110, oldPrice: 140, stock: 30, spec: '2kg装', model: 'R410a-2', desc: 'R410a环保冷媒，2kg装' },
	{ title: 'R32冷媒', type: 'refrigerant', price: 52, oldPrice: 68, stock: 40, spec: '1kg装', model: 'R32-1', desc: 'R32环保冷媒，1kg装' },
	{ title: '冷媒补充剂', type: 'refrigerant', price: 35, oldPrice: 45, stock: 55, spec: '500ml', model: 'LM-500', desc: '冷媒补充剂，500ml装' },
	{ title: 'R290碳氢冷媒', type: 'refrigerant', price: 45, oldPrice: 58, stock: 38, spec: '500g', model: 'R290-05', desc: 'R290碳氢环保冷媒' },
	{ title: '复合冷媒', type: 'refrigerant', price: 60, oldPrice: 78, stock: 32, spec: '1kg装', model: 'LM-FH', desc: '复合冷媒，1kg装通用型' },
	// 辅材类 +4
	{ title: '加厚保温管', type: 'aux', price: 18, oldPrice: 24, stock: 160, spec: '12mm 3米', model: 'BW-12', desc: '12mm加厚保温管，3米' },
	{ title: '排水管弯头', type: 'aux', price: 8, oldPrice: 12, stock: 280, spec: '16mm 弯头', model: 'PS-WT', desc: '16mm排水管弯头' },
	{ title: '尼龙扎带包', type: 'aux', price: 10, oldPrice: 14, stock: 350, spec: '100根装', model: 'ND-100', desc: '尼龙扎带，100根装' },
	{ title: '膨胀螺丝包', type: 'aux', price: 6, oldPrice: 9, stock: 400, spec: '20套装', model: 'PZ-20', desc: '膨胀螺丝，20套装' }
]

let _cid = 100
export const mockConsumables = [
	..._consumableBase,
	..._consumableExtra.map(t => {
		_cid++
		const isHot = _cid % 7 === 0
		return {
			id: _cid,
			image: `https://picsum.photos/200/200?random=${500 + _cid}`,
			tag: isHot ? '热销' : '',
			tagColor: isHot ? '#ff4d4f' : '',
			sales: `${2 + (_cid % 8)}000+`,
			tags: ['正品保证'],
			detailImages: [`https://picsum.photos/750/500?random=${600 + _cid}`],
			category: _categoryMap[t.type],
			...t
		}
	})
]

// ====================== 订单（供 order 列表 / order-detail 详情） ======================
// 字段已与页面模板直接对齐，res.data 即可渲染，无需 mapXxx：
//  - status 直接存中文；tools 内字段与耗材统一用 title（含 id/title/spec/qty/image/price 便于详情回填）
// 程序化生成：3 状态 × 8 条 = 24 条，保证每个 tab 切换都能体现分页
const _orderStatuses = ['待上门', '处理中', '已完成']
const _orderTemplates = [
	{ serviceName: '空调上门安装服务', productName: '格力1.5匹空调挂机', productSpec: '新一级能效 变频双排蒸发器' },
	{ serviceName: '空调维修服务', productName: '美的3匹柜机', productSpec: '定频单排冷凝器' },
	{ serviceName: '空调移机服务', productName: '海尔1.5匹挂机', productSpec: '变频节能静音' },
	{ serviceName: '空调清洗保养', productName: '奥克斯2匹柜机', productSpec: '一级能效' },
	{ serviceName: '空调上门安装服务', productName: '海信1.5匹挂机', productSpec: '新一级变频' },
	{ serviceName: '空调维修服务', productName: '格力5匹天井机', productSpec: '商用定频' },
	{ serviceName: '空调清洗保养', productName: '志高1.5匹挂机', productSpec: '三级能效' },
	{ serviceName: '空调移机服务', productName: 'TCL2匹柜机', productSpec: '二级能效' }
]
const _customerNames = ['张先生', '李女士', '王先生', '赵女士', '陈先生', '刘先生', '周女士', '吴先生', '孙女士', '马先生', '黄女士', '胡先生']
const _customerPhones = ['138****2353', '139****8866', '137****4521', '135****9988', '136****1234', '133****5678', '138****6611', '139****2233', '137****7788', '135****4455', '136****8899', '133****1122']
const _districts = ['东西湖区金山大道', '江汉区解放大道', '武昌区中南路', '洪山区珞瑜路', '汉阳区龙阳大道', '硚口区解放大道', '青山区和平大道', '江岸区建设大道']

export const mockOrders = []
_orderStatuses.forEach(status => {
	_orderTemplates.forEach((t, i) => {
		const id = mockOrders.length + 1
		const isDone = status === '已完成'
		mockOrders.push({
			id,
			image: `https://picsum.photos/200/200?random=${400 + id}`,
			serviceName: t.serviceName,
			productName: t.productName,
			productSpec: t.productSpec,
			quantity: (i % 2) + 1,
			status,
			orderNo: `XL2026080${String(id).padStart(3, '0')}`,
			name: _customerNames[(id - 1) % _customerNames.length],
			phone: _customerPhones[(id - 1) % _customerPhones.length],
			address: `湖北省武汉市${_districts[(id - 1) % _districts.length]}${20 + id * 3}号`,
			visitTime: `2026年8月${(id % 28) + 1}日${10 + (id % 8)}:00`,
			completeText: isDone ? '服务已完成，经测试运行正常，客户确认验收满意。' : '',
			tools: isDone && i % 2 === 0
				? [{ id: 1, title: '铜连接管', spec: '3米', qty: 2, image: 'https://picsum.photos/200/200?random=502', price: 35 }]
				: (isDone ? [{ id: 4, title: 'R410a环保冷媒', spec: '1kg装', qty: 1, image: 'https://picsum.photos/200/200?random=505', price: 65 }] : []),
			images: isDone ? [`https://picsum.photos/300/300?random=${100 + id}`, `https://picsum.photos/300/300?random=${200 + id}`] : []
		})
	})
})

// ====================== 资讯/公告（供 notice 列表 / notices-detail 详情） ======================
// 字段已与页面模板直接对齐（title/desc/content/date/views/image），res.data 即可渲染，无需 mapXxx
const _dynamicsExtra = [
	{ title: '空调漏水怎么办？五大常见原因分析', desc: '空调漏水是夏季常见故障，本文为您梳理五大原因及处理建议。', content: '<p>空调漏水常见原因：排水管堵塞、安装倾斜、冷媒不足、蒸发器结冰、接水盘损坏。</p><p>建议先检查排水管是否通畅，再排查安装角度。</p>' },
	{ title: '如何判断空调是否缺氟？', desc: '制冷效果变差可能是缺氟，教你几个简单判断方法。', content: '<p>缺氟判断：出风温度不够低、外机铜管结霜、运行电流偏低、制冷剂压力低于标准值。</p>' },
	{ title: '空调噪音大的解决方法', desc: '空调异响影响休息？本文分析噪音来源及降噪方案。', content: '<p>空调噪音来源：内机风叶松动、外机支架共振、压缩机老化、缺氟导致异响。</p>' },
	{ title: '夏季空调节电小妙招', desc: '正确使用空调既清凉又省电，这些技巧你都知道吗？', content: '<p>节电技巧：温度设26℃最省电、配合风扇使用、定期清洗滤网、避免频繁开关机。</p>' },
	{ title: '中央空调与分体空调如何选择', desc: '装修选空调纠结中央还是分体？本文从价格、效果、维护全方位对比。', content: '<p>中央空调美观但造价高、维护复杂；分体空调灵活、性价比高，适合中小户型。</p>' },
	{ title: '空调滤网清洗详细步骤', desc: '定期清洗滤网能提升制冷效果、省电又健康，手把手教你操作。', content: '<p>清洗步骤：断电→打开面板→取下滤网→清水冲洗→晾干→装回。建议每两周清洗一次。</p>' },
	{ title: '空调使用年限与更换建议', desc: '空调用多久该换？超龄使用有哪些隐患？', content: '<p>空调一般使用年限8-10年，超龄空调能耗高、制冷差、存在安全隐患，建议及时更换。</p>' },
	{ title: '鑫立创夏季服务月活动开启', desc: '清凉一夏，鑫立创推出安装清洗特惠套餐，详情咨询客服。', content: '<p>夏季服务月：空调安装8折、清洗保养套餐立减50元、老客户推荐有礼。</p>' }
]
let _did = 6
export const mockDynamics = [
	{ id: 1, title: '夏季空调保养全攻略：让清凉更持久', desc: '炎炎夏日，空调是我们生活中不可或缺的伙伴。正确的保养不仅能延长空调寿命，还能节省电费。', content: '<p>夏季空调保养指南正文内容，包含滤网清洗、温度设置建议、定期检查等专业知识。</p><p>建议每两周清洗一次滤网，温度设置在26℃最为节能。</p>', date: '2026-07-28', views: 1280, image: 'https://picsum.photos/400/250?random=701' },
	{ id: 2, title: '变频空调与定频空调的区别，如何选择？', desc: '买空调时经常听到变频和定频，它们到底有什么区别？哪种更适合家庭使用？', content: '<p>变频空调通过调节压缩机转速实现恒温，更节能舒适；定频空调靠频繁启停控温，价格更低。</p>', date: '2026-07-25', views: 960, image: 'https://picsum.photos/400/250?random=702' },
	{ id: 3, title: '空调安装注意事项，这些细节不能忽略', desc: '空调安装位置、管道布局、支架固定等细节直接影响使用效果和安全。', content: '<p>空调安装是一项专业工作，涉及位置选择、管道连接、电路安全等多个方面。</p>', date: '2026-07-20', views: 1520, image: 'https://picsum.photos/400/250?random=703' },
	{ id: 4, title: '冬季空调不制热？常见原因与解决方法', desc: '冬天空调不制热是常见问题，本文为您分析可能原因并提供解决方案。', content: '<p>空调不制热可能原因：缺氟、四通阀故障、化霜保护、室外温度过低等。</p>', date: '2026-07-15', views: 780, image: 'https://picsum.photos/400/250?random=704' },
	{ id: 5, title: '鑫立创制冷荣获约克品牌授权服务商', desc: '热烈祝贺我司成为约克(中国)正式授权服务商，为您提供更专业的服务。', content: '<p>武汉力创之尊机电设备有限公司（鑫立创）正式获得约克(中国)品牌授权，成为其官方授权服务商。</p>', date: '2026-07-10', views: 2100, image: 'https://picsum.photos/400/250?random=705' },
	{ id: 6, title: '空调匹数怎么选？一篇文章告诉你', desc: '1匹、1.5匹、2匹、3匹分别适用多大房间？本文手把手教你选择。', content: '<p>空调匹数选择：1匹适用10-15㎡，1.5匹适用15-20㎡，2匹适用20-30㎡，3匹适用30-40㎡。</p>', date: '2026-07-05', views: 1850, image: 'https://picsum.photos/400/250?random=706' },
	..._dynamicsExtra.map(t => {
		_did++
		return {
			id: _did,
			...t,
			date: `2026-07-${String(28 - _did).padStart(2, '0')}`,
			views: 500 + _did * 120,
			image: `https://picsum.photos/400/250?random=${700 + _did}`
		}
	})
]

// ====================== 当前登录用户信息（供 user / settings） ======================
export const mockUserInfo = {
	nickname: '体验用户',
	phone: '138****8888',
	role: 'user',
	avatar: ''
}

// ====================== 订单评价（供 order-evaluate / order-detail） ======================
// 字段已与页面模板直接对齐：{ orderId, score, content, images, createTime, labels }
// 默认给部分已完成订单预设评价，便于演示"师傅查看评价"与"空状态"
export const mockEvaluations = [
	{
		orderId: 17,
		score: 5,
		content: '师傅非常专业，安装效率高，态度也很好，五星好评！',
		images: ['https://picsum.photos/300/300?random=901', 'https://picsum.photos/300/300?random=902'],
		createTime: '2026-08-02 14:30',
		labels: ['超赞']
	},
	{
		orderId: 18,
		score: 4,
		content: '整体不错，就是预约时间稍微晚了一点，其他方面都满意。',
		images: [],
		createTime: '2026-08-01 10:15',
		labels: ['推荐']
	},
	{
		orderId: 20,
		score: 3,
		content: '安装过程比较顺利，但收尾清理一般般，希望改进。',
		images: ['https://picsum.photos/300/300?random=903'],
		createTime: '2026-07-30 16:45',
		labels: ['一般']
	}
]

// 根据订单 ID 查找评价（兼容字符串/数字）
export function getEvaluationByOrderId(orderId) {
	return mockEvaluations.find(item => String(item.orderId) === String(orderId))
}

// ====================== 工作台概览（供 user 页 stats） ======================
export const mockDashboard = {
	pending: 8,
	processing: 6,
	done: 24
}

// ====================== 辅助函数 ======================

/**
 * 判断当前是否处于 mock 模式（token 以 mock_ 开头）
 */
export function isMockMode() {
	const t = uni.getStorageSync('token') || ''
	return t.startsWith('mock_')
}

/**
 * 构造 mock 成功响应体（与后端统一返回结构一致）
 * 带模拟网络延迟，确保下拉刷新/上拉加载/详情加载的 loading 状态可见
 * （切换真实接口后由真实网络延迟自然提供，页面代码无需修改）
 * @param {*} data 任意 mock 数据
 * @returns {Promise<{ code:200, data:*, msg:'ok' }>}
 */
export function mockSuccess(data) {
	return new Promise((resolve) => {
		setTimeout(() => {
			resolve({ code: 200, data, msg: 'ok' })
		}, 400)
	})
}

/**
 * mock 分页 + 通用过滤（订单按 status、耗材按 category/type、全部支持 keyword）
 * @param {Array} list  源数据
 * @param {Object} params  { page, pageSize, status, category, keyword }
 * @returns {{ list:Array, total:number }}
 */
export function mockPaging(list, params = {}) {
	const page = params.page || 1
	const pageSize = params.pageSize || 10
	let filtered = list
	if (params.status && params.status !== 'all') {
		filtered = filtered.filter(o => o.status === params.status)
	}
	if (params.category && params.category !== 'all') {
		// 耗材分类字段为 type
		filtered = filtered.filter(o => o.type === params.category)
	}
	if (params.keyword) {
		const kw = params.keyword
		filtered = filtered.filter(o => {
			const t = o.title || o.serviceName || o.name || ''
			return t.indexOf(kw) !== -1
		})
	}
	const start = (page - 1) * pageSize
	return {
		list: filtered.slice(start, start + pageSize),
		total: filtered.length
	}
}
