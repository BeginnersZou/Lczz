<template>
	<view class="agreement-page">
		<!-- 文档标题区 -->
		<view class="agreement-card title-card">
			<text class="doc-title">隐私保护协议</text>
			<view class="doc-meta">
				<text class="meta-text">版本号：v1.1.0</text>
				<text class="meta-text">更新日期：2026年8月25日</text>
			</view>
		</view>

		<!-- 前言 -->
		<view class="agreement-card">
			<text class="chapter-title">前言</text>
			<text class="chapter-text">武汉力创之尊机电设备有限公司（品牌：鑫立创，以下简称“我们”）深知个人信息对您的重要性。我们将严格按照《中华人民共和国个人信息保护法》《中华人民共和国网络安全法》等相关法律法规的规定，保护您的个人信息及隐私安全。本协议说明我们如何收集、使用、存储、共享和保护您的个人信息，请您仔细阅读。</text>
		</view>

		<!-- 一、信息收集范围 -->
		<view class="agreement-card">
			<text class="chapter-title">一、信息收集范围</text>
			<text class="chapter-text">在您使用本小程序过程中，我们会收集以下信息：</text>
			<text class="chapter-text">1. 微信登录标识：通过微信登录获取的 openid，用于识别账号及维持登录状态；</text>
			<text class="chapter-text">2. 手机号码：在获得您的明示同意后获取您选择的微信绑定手机号，用于用户注册、登录、身份验证、账号管理及订单服务联系；</text>
			<text class="chapter-text">3. 订单服务信息：为提供安装、维修等服务，我们会处理客户姓名、联系电话、服务地址、订单内容、施工进度、耗材记录和评价内容；</text>
			<text class="chapter-text">4. 选中的照片或视频信息：仅在您主动选择上传时获取，用于提交订单附件、施工进度、完工凭证及订单评价图片；</text>
			<text class="chapter-text">5. 登录日志：为保障账号和服务安全，我们会记录必要的登录时间及 IP 地址。</text>
		</view>

		<!-- 二、信息使用目的 -->
		<view class="agreement-card">
			<text class="chapter-title">二、信息使用目的</text>
			<text class="chapter-text">我们收集的信息将仅用于以下目的：</text>
			<text class="chapter-text">1. 为您提供账号登录、身份识别及订单管理服务；</text>
			<text class="chapter-text">2. 安排师傅上门提供安装、维修、清洗等服务；</text>
			<text class="chapter-text">3. 保存施工进度、完工凭证与评价记录，用于服务质量追溯；</text>
			<text class="chapter-text">4. 向您发送服务通知、订单状态变更等必要消息；</text>
			<text class="chapter-text">5. 改善产品功能与服务体验。我们不会将您的信息用于本协议未载明的其他用途。</text>
		</view>

		<!-- 三、信息存储与保护 -->
		<view class="agreement-card">
			<text class="chapter-title">三、信息存储与保护</text>
			<text class="chapter-text">1. 您的个人信息存储于中华人民共和国境内的服务器，存储期限为提供服务所必需的合理期限；</text>
			<text class="chapter-text">2. 我们采用加密传输、权限控制、安全审计等技术手段和管理措施，防止信息泄露、损毁或丢失；</text>
			<text class="chapter-text">3. 仅经授权的人员在必要范围内方可访问您的个人信息，并承担保密义务；</text>
			<text class="chapter-text">4. 如发生信息安全事件，我们将及时启动应急预案并依法告知您。</text>
		</view>

		<!-- 四、信息共享 -->
		<view class="agreement-card">
			<text class="chapter-title">四、信息共享与披露</text>
			<text class="chapter-text">1. 为完成上门服务，我们会在必要范围内向为您服务的师傅共享订单地址、联系方式等信息；</text>
			<text class="chapter-text">2. 除上述情形外，我们不会向任何第三方共享、出售您的个人信息；</text>
			<text class="chapter-text">3. 因法律法规要求或行政、司法机关依法调取的，我们将依法配合披露相关信息。</text>
		</view>

		<!-- 五、用户权利 -->
		<view class="agreement-card">
			<text class="chapter-title">五、您的权利</text>
			<text class="chapter-text">1. 查询、更正：您可在"我的"页面查询和更正个人信息；</text>
			<text class="chapter-text">2. 删除：您可联系我们删除已不再需要的个人信息；</text>
			<text class="chapter-text">3. 撤回授权：您可通过微信设置解除小程序授权，我们将停止信息收集；</text>
			<text class="chapter-text">4. 账号注销：您可通过下方联系方式申请注销账号。我们将在受理后十五个工作日内完成核查和处理，并依法删除或匿名化相关个人信息。</text>
		</view>

		<!-- 六、Cookie/Token 使用 -->
		<view class="agreement-card">
			<text class="chapter-title">六、Token 使用说明</text>
			<text class="chapter-text">为保障您的登录状态与请求安全，我们使用 Token 机制：登录成功后由服务器颁发 Token 并存储于您本地设备，后续请求自动携带以验证身份。Token 不包含您的敏感个人信息明文，退出登录后即从本地清除。</text>
		</view>

		<!-- 七、未成年人保护 -->
		<view class="agreement-card">
			<text class="chapter-title">七、未成年人保护</text>
			<text class="chapter-text">若您是十四周岁以下的未成年人，请与监护人共同阅读本协议，并在征得监护人明示同意后使用本服务。我们将依法保护经监护人同意而处理的未成年人个人信息。</text>
		</view>

		<!-- 八、协议更新 -->
		<view class="agreement-card">
			<text class="chapter-title">八、协议更新与联系我们</text>
			<text class="chapter-text">1. 本协议可能因业务调整或法律法规变化而更新，更新后将在小程序内公示，请您定期查阅；</text>
			<text class="chapter-text">2. 若您对本协议或个人信息保护有任何疑问，可通过以下方式联系我们：</text>
			<text class="chapter-text">公司：武汉力创之尊机电设备有限公司</text>
			<text class="chapter-text">联系电话：18086015896</text>
			<text class="chapter-text">我们将在收到您的反馈后及时处理并回复。</text>
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
