<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<style>
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html,
body,
#app {
  height: 100%;
}

body {
  color: var(--text-primary);
  background: var(--surface-page);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-size: 14px;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
}

body,
.el-table,
.el-pagination {
  font-variant-numeric: tabular-nums;
}

button,
input,
textarea,
select {
  font: inherit;
}

.main-content :is(.order-page, .user-page, .audit-page, .dynamic-page, .dashboard) {
  min-height: auto !important;
  padding: 0 !important;
  background: transparent !important;
}

.main-content :is(.order-page, .user-page, .audit-page, .dynamic-page, .dashboard) > .page-header,
.main-content :is(.order-form-page, .publish-page, .detail-edit-page, .audit-detail-page, .detail-page) > .page-header {
  min-height: 68px !important;
  margin: 0 !important;
  padding: 0 4px 16px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: space-between !important;
  gap: 20px !important;
  background: transparent !important;
  border: 0 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
}

.main-content .page-title {
  margin: 0 !important;
  color: var(--text-primary) !important;
  font-size: 20px !important;
  font-weight: 600 !important;
  line-height: 1.35 !important;
  letter-spacing: -0.2px;
}

.main-content .page-desc {
  display: block;
  margin-top: 5px !important;
  color: var(--text-secondary) !important;
  font-size: 13px !important;
  line-height: 1.5 !important;
}

.main-content .page-header .header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.main-content .page-header .header-right .el-button + .el-button {
  margin-left: 0;
}

.main-content .search-card {
  margin: 0 !important;
  background: var(--surface-card) !important;
  border: 1px solid var(--border-default) !important;
  border-bottom: 0 !important;
  border-radius: var(--radius-card) var(--radius-card) 0 0 !important;
  box-shadow: none !important;
}

.main-content .dynamic-page .search-card {
  margin-bottom: 16px !important;
  border-bottom: 1px solid var(--border-default) !important;
  border-radius: var(--radius-card) !important;
}

.main-content .dynamic-page > .el-pagination-style {
  margin-top: 16px !important;
  padding-right: 0 !important;
  background: transparent;
  border: 0;
}

.main-content .search-card .el-card__body {
  padding: 16px 20px !important;
}

.main-content .search-card .search-bar,
.main-content .search-card .search-item,
.main-content .search-card .el-form {
  display: flex !important;
  align-items: center !important;
  flex-wrap: wrap !important;
  gap: 12px !important;
}

.main-content .search-card .el-form-item {
  margin: 0 !important;
}

.main-content .search-card .search-input {
  width: 280px !important;
}

.main-content .search-card :is(.status-select, .role-select, .category-select) {
  width: 180px !important;
}

.main-content .search-card .el-button + .el-button {
  margin-left: 0;
}

.main-content .table-card {
  margin: 0 !important;
  overflow: hidden;
  border: 1px solid var(--border-default) !important;
  border-radius: 0 0 var(--radius-card) var(--radius-card) !important;
  box-shadow: var(--shadow-card) !important;
}

.main-content .table-card .el-table__body-wrapper,
.main-content .table-card .el-scrollbar__wrap {
  scrollbar-gutter: stable;
}

.main-content .table-card > .el-card__body {
  padding: 0 !important;
}

.main-content :is(.pagination-wrap, .el-pagination-style) {
  min-height: 58px;
  margin: 0 !important;
  padding: 14px 20px !important;
  display: flex !important;
  align-items: center;
  justify-content: flex-end !important;
  background: #fff;
  border-top: 1px solid var(--border-default);
}

.primary-cell {
  color: var(--text-primary);
  font-weight: 500;
  line-height: 1.5;
}

.datetime-cell,
.code-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  color: var(--text-secondary);
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-cell {
  color: var(--text-primary);
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 13px;
}

.secondary-cell {
  margin-top: 2px;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.4;
}

.error-state {
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--text-secondary);
  background: #fff;
  border: 0;
  border-radius: 0;
}

.error-state::before {
  content: "!";
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  color: var(--brand-warning);
  background: #fff7ed;
  border-radius: 50%;
  font-size: 20px;
  font-weight: 700;
}

.loading-state {
  min-height: 240px;
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-card);
}

.dialog-tip {
  margin: 0 0 16px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.main-content :is(.order-form-page, .publish-page, .detail-edit-page, .audit-detail-page, .detail-page) {
  padding: 0 !important;
  background: transparent !important;
}

.main-content .form-card,
.main-content .info-card,
.main-content .material-card {
  margin-bottom: 16px !important;
  border: 1px solid var(--border-default) !important;
  border-radius: var(--radius-card) !important;
  box-shadow: var(--shadow-card) !important;
}

.main-content .form-card .el-card__header,
.main-content .info-card .el-card__header,
.main-content .material-card .el-card__header {
  padding: 16px 20px !important;
  background: #fff !important;
}

.main-content .form-card .el-card__body {
  padding: 24px !important;
}

.main-content :is(.form-content, .publish-form, .detail-content) {
  width: min(100%, 1180px) !important;
  margin-right: auto !important;
  margin-left: auto !important;
}

.main-content :is(.order-form-page, .publish-page, .detail-edit-page, .audit-detail-page) > .page-header {
  width: min(100%, 1180px);
  margin-right: auto !important;
  margin-left: auto !important;
  justify-content: flex-start !important;
}

.main-content :is(.order-form-page, .publish-page, .detail-edit-page, .audit-detail-page) > .page-header .back-btn {
  flex: 0 0 auto;
}

.main-content :is(.order-form-page, .publish-page, .detail-edit-page, .audit-detail-page) > .page-header .header-empty {
  display: none;
}

.main-content .form-card .el-form-item:last-child {
  margin-bottom: 0;
}

.main-content .card-title {
  color: var(--text-primary) !important;
  font-size: 16px !important;
  font-weight: 600 !important;
}

.main-content .form-footer {
  margin-top: 16px !important;
  padding: 14px 0 20px !important;
  border-top: 1px solid var(--border-default) !important;
}

.uploading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #fff;
  background: rgba(17, 24, 39, 0.62);
  border-radius: 6px;
}

.form-load-error {
  max-width: 960px;
  margin: 0 auto 16px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-card);
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}

@media (max-width: 768px) {
  .el-dialog {
    width: calc(100vw - 32px) !important;
  }

  .main-content :is(.order-page, .user-page, .audit-page, .dynamic-page, .dashboard) > .page-header,
  .main-content :is(.order-form-page, .publish-page, .detail-edit-page, .audit-detail-page, .detail-page) > .page-header {
    min-height: auto !important;
    align-items: flex-start !important;
    flex-direction: column !important;
  }

  .main-content .search-card .search-input,
  .main-content .search-card :is(.status-select, .role-select, .category-select) {
    width: 100% !important;
  }

  .main-content :is(.form-content, .publish-form, .detail-content) {
    width: 100% !important;
  }
}
</style>
