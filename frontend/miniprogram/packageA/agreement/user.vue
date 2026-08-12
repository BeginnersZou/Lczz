<template>
	<view class="agreement-page">
		<!-- 文档标题区 -->
		<view class="agreement-card title-card">
			<text class="doc-title">用户服务协议</text>
			<view class="doc-meta">
				<text class="meta-text">版本号：v1.0.0</text>
				<text class="meta-text">生效日期：2026年8月3日</text>
			</view>
		</view>

		<!-- 前言 -->
		<view class="agreement-card">
			<text class="chapter-title">前言</text>
			<text class="chapter-text">欢迎使用武汉力创之尊机电设备有限公司（品牌：鑫立创，以下简称“我们”）提供的空调服务小程序。本协议是您与我们就使用本小程序所订立的协议。请您在使用本服务前仔细阅读本协议，一旦您勾选同意并使用本服务，即视为您已充分理解并接受本协议全部条款。</text>
		</view>

		<!-- 一、服务说明 -->
		<view class="agreement-card">
			<text class="chapter-title">一、服务说明</text>
			<text class="chapter-text">1. 本小程序为您提供空调安装、维修、清洗、移机等服务预约、订单管理及完工记录查询等功能；</text>
			<text class="chapter-text">2. 我们有权根据业务发展对服务内容进行调整或新增，相关调整将在小程序内公示；</text>
			<text class="chapter-text">3. 部分功能可能因版本迭代暂未开放，具体以小程序实际提供为准。</text>
		</view>

		<!-- 二、账号与登录 -->
		<view class="agreement-card">
			<text class="chapter-title">二、账号注册与登录</text>
			<text class="chapter-text">1. 您可通过微信授权方式登录本小程序，首次登录需授权手机号完成注册；</text>
			<text class="chapter-text">2. 您应确保注册信息真实、有效，并对账号及密码的安全保管承担责任；</text>
			<text class="chapter-text">3. 因您保管不善导致账号被盗用或信息泄露的，由您自行承担相应后果；</text>
			<text class="chapter-text">4. 发现账号被盗或异常使用，请立即联系我们处理。</text>
		</view>

		<!-- 三、权利与义务 -->
		<view class="agreement-card">
			<text class="chapter-title">三、双方权利与义务</text>
			<text class="chapter-text">1. 我们将按约定标准为您安排师傅提供上门服务，并保障服务质量；</text>
			<text class="chapter-text">2. 您应按约提供准确的地址、联系方式，并配合师傅完成服务；</text>
			<text class="chapter-text">3. 您不得利用本小程序从事违法活动或损害他人合法权益；</text>
			<text class="chapter-text">4. 您上传的安装图片及信息应真实合法，不得包含违法或侵权内容。</text>
		</view>

		<!-- 四、订单规则 -->
		<view class="agreement-card">
			<text class="chapter-title">四、订单处理规则</text>
			<text class="chapter-text">1. 订单提交后，我们将在合理时间内安排师傅接单并与您确认上门时间；</text>
			<text class="chapter-text">2. 订单状态包括待上门、备货中、处理中、已完成、已作废等，您可在订单列表实时查看；</text>
			<text class="chapter-text">3. 服务完成后，师傅将提交完工信息及安装图片，您可进行查看与确认；</text>
			<text class="chapter-text">4. 因故需取消订单的，请提前通过小程序申请作废。</text>
		</view>

		<!-- 五、知识产权 -->
		<view class="agreement-card">
			<text class="chapter-title">五、知识产权</text>
			<text class="chapter-text">1. 本小程序的界面设计、图标、文案、商标等知识产权归我们所有，未经授权不得复制或转载；</text>
			<text class="chapter-text">2. 您不得以任何方式篡改、反向工程本小程序，或破坏其正常运行；</text>
			<text class="chapter-text">3. 您上传的内容应保证不侵犯第三方知识产权，否则由您承担全部责任。</text>
		</view>

		<!-- 六、协议变更 -->
		<view class="agreement-card">
			<text class="chapter-title">六、协议变更</text>
			<text class="chapter-text">1. 我们有权根据法律法规及业务需要修改本协议，修改后的协议自在小程序公示之日起生效；</text>
			<text class="chapter-text">2. 若您不同意修改后的协议，可停止使用本服务；继续使用即视为接受修改后的协议。</text>
		</view>

		<!-- 七、争议解决 -->
		<view class="agreement-card">
			<text class="chapter-title">七、免责与争议解决</text>
			<text class="chapter-text">1. 因不可抗力或第三方原因导致服务中断的，我们不承担责任；</text>
			<text class="chapter-text">2. 本协议的订立、效力、解释及争议解决均适用中华人民共和国法律；</text>
			<text class="chapter-text">3. 因本协议或本服务产生的争议，双方应友好协商解决；协商不成的，任何一方均可向我们所在地有管辖权的人民法院提起诉讼。</text>
		</view>

		<!-- 八、联系方式 -->
		<view class="agreement-card">
			<text class="chapter-title">八、联系方式</text>
			<text class="chapter-text">公司名称：武汉力创之尊机电设备有限公司</text>
			<text class="chapter-text">联系邮箱：service@lczzcompany.com</text>
			<text class="chapter-text">如您对本协议有任何疑问，欢迎通过上述方式与我们联系，我们将竭诚为您服务。</text>
		</view>
	</view>
</template>

<script setup>
</script>

<style lang="scss">
@import "@/uni.scss";

.agreement-page {
	min-height: 100vh;
	background-color: $bg-page;
	padding: $space-lg $space-lg $space-xl;
	box-sizing: border-box;
}

.agreement-card {
	background-color: $bg-card;
	border-radius: $radius-xl;
	padding: $space-lg;
	margin-bottom: $space-md;
	box-shadow: $shadow-card;
	display: flex;
	flex-direction: column;
}

.title-card {
	align-items: center;
	text-align: center;
	padding: $space-xl $space-lg;
}

.doc-title {
	font-size: $font-xxl;
	color: $text-main;
	font-weight: 700;
	line-height: 1.4;
}

.doc-meta {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 8rpx;
	margin-top: $space-sm;
}

.meta-text {
	font-size: $font-sm;
	color: $text-light;
}

.chapter-title {
	font-size: $font-lg;
	color: $primary;
	font-weight: 600;
	margin-bottom: $space-sm;
}

.chapter-text {
	font-size: $font-md;
	color: $text-sub;
	line-height: 1.8;
	margin-bottom: 8rpx;
}
</style>
