# J-Harness Web Workbench

用户已指定 DeepSeek Harness 本地源码为视觉权威，不重新选择设计风格。

- Operate：左侧任务导航，中间会话/事件与输入区，右侧按需展开详情；使用原始 ui-theme 的 base/design-platform/focus/scrollbar 样式。
- 系统中文字体，代码使用等宽字体；沿用原主题变量的浅色/深色、8/12px 控件圆角和中性分层背景。
- 直接移植 Button/Input/Tag/分段控件等可用原子及其 CSS；保留 hover/active 状态、侧栏离散折叠过渡与拖拽时不缓动的行为。
- 任务状态以服务端事实为准；审批显示参数、影响及关联，UNKNOWN 明确解释且无自动重试入口。
- 宽屏可拖拽三栏；窄屏使用可关闭导航/详情面板；键盘焦点可见，减少动态效果时关闭位移动画。
- 名称、图标和文案属于 J-Harness；原始代码来源与修改记录见 frontend/upstream.json，保留 MIT。
