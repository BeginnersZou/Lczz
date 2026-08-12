//新增十位数时间戳  1766370145 转换成正常时间 格式 公共js 导出供别的组件使用


//10位数时间戳转换成正常的时间
export function timeTransFrom(time) {
	const date = new Date(time * 1000)
	const year = date.getFullYear()
	const month = date.getMonth() + 1
	const day = date.getDate()
	const hour = date.getHours()
	const minute = date.getMinutes()
	const second = date.getSeconds()
	return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}