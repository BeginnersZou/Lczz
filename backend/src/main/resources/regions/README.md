# 行政区划数据说明

`china-regions.json` 基于 `uiwjs/province-city-china` 的 `gh-pages/level.json` 生成，源文件提交为
`d1c606616220c668393a95238f15e117a9853b44`。只保留接口所需的 `code`、`name`、`children` 字段。

为保证后台三级联选择器始终可以选择三个层级，直辖市、港澳以及上游缺少下级数据的节点增加了仅用于界面分组的层级。
数据随应用打包，服务运行时不会访问外部网络。

来源：https://github.com/uiwjs/province-city-china

许可：MIT，见 `LICENSE-province-city-china.txt`。
