# OCR逐条核实：delivery

原文及建议代码见 opinions.json；这里只以稳定编号连接原意见，部分采纳以“判断”限定范围。所有修复均未实施。

## OCR-0001 · 部分采纳

- 位置：frontend/src/upstream/Pill.module.css:20；OCR原严重度：high
- 计划：R4-04 / P3
- 原意见：可复现的状态覆盖缺陷：`.interactive:hover`（0,2,0）比 `.active`（0,1,0）特异性高，且 `Pill.tsx` 第 29 行会同时输出 `interactive` 与 `active`。触发条件：鼠标悬停在一个已选中的可交互 Pill 上——背景会从 `--dsw-alias-button-ghost-active-fill` 变为 hover 背景，而选中态的 inset 边框仍然保留，导致“选中”视觉被破坏，且在移开鼠标前状态看起来介于两者之间。建议用 `:not(.active)` 排除选中态。
- 判断：interactive:hover特异性高于active，但选中box-shadow仍在，hover视觉本身可为设计选择；采纳选中+hover状态明确化/视觉回归，不接受选中逻辑被破坏的高严重度结论。
- 证据：frontend/src/upstream/Pill.module.css:20

## OCR-0002 · 部分采纳

- 位置：frontend/src/upstream/Pill.module.css:16；OCR原严重度：medium
- 计划：R4-04 / P3
- 原意见：可交互态缺少键盘焦点与禁用态样式：`.pill` 是 `<button>` 时，浏览器默认 outline 可能被全局样式重置，而此文件没有任何 `:focus-visible` 规则，键盘用户无法确认当前焦点落在哪个 Pill 上；同时 `.interactive` 无条件设置 `cursor: pointer`，而 `Pill.tsx` 会把 `...rest`（含 `disabled`）透传给 button，因此禁用的 Pill 仍显示手型光标与 hover 背景。建议补充 `:focus-visible` 与 `:disabled`（或 `:not(:disabled)` 限定 hover/pointer）。
- 判断：全局focus.css保留原生outline，Pill并非必然无键盘焦点；disabled仍可命中hover/cursor属实。仅采纳禁用视觉及验证现有focus，不盲加重复ring。
- 证据：frontend/src/upstream/Pill.module.css:16

## OCR-0003 · 部分采纳

- 位置：frontend/src/upstream/Pill.module.css:27；OCR原严重度：medium
- 计划：R4-04 / P2
- 原意见：选中态边框仅靠 inset box-shadow 模拟，且基础规则已 `border: none`：在 forced-colors / 高对比度模式下 box-shadow 会被丢弃，选中 Pill 与普通 Pill 之间将不再有可见边界（颜色 token 也被系统色替换）。建议保留一条透明真实边框并在 active 时着色（需同时加 `box-sizing: border-box` 以维持 24px 高度），或显式增加 `@media (forced-colors: active)` 规则。
- 判断：active边界只靠box-shadow，在forced-colors可能丢选中区别；采纳高对比模式的真实边界/选中指示。保持上游文件字节约束，以适配层或有来源更新实现。
- 证据：frontend/src/upstream/Pill.module.css:27

## OCR-0004 · 不采纳

- 位置：frontend/src/upstream/Pill.module.css:12；OCR原严重度：medium
- 计划：无
- 原意见：所有颜色 token 都没有 fallback：一旦 `--dsw-*` 变量未定义或值为空（例如主题文件未加载、变量作用域不匹配），`background` / `color` 声明会整体失效，Pill 将退化为透明背景 + 继承父级文字色，此时 `.active` 与普通态的差异可能完全消失，用户无法判断当前选中项。建议为每个 var() 提供系统色兜底，例如 `Canvas` / `CanvasText` / `Highlight`，确保 token 缺失时仍可读且状态可辨。
- 判断：主题在main.tsx固定加载，相关token在受管主题中定义；假设主题被删/覆盖非法值或隔离组件不是当前支持调用。每个var增加fallback无实际缺失证据，不批量改上游。
- 证据：frontend/src/upstream/Pill.module.css:12

## OCR-0005 · 部分采纳

- 位置：frontend/src/upstream/Tag.module.css:7；OCR原严重度：medium
- 计划：R4-04 / P3
- 原意见：基础 `.tag` 没有声明 border，而 `.tag[data-tone='outline']`（第 16-19 行）额外加了 `border: 0.5px solid ...`，且高度完全由 line-height+padding 决定、没有固定 height，因此 `box-sizing` 也救不了。结果是 outline 标签左右各多 0.5px、上下各多 0.5px，整体比其他 tone 宽/高 1px（在把 0.5px 向上取整的引擎里可能到 2px），与文件头注释“a tag reads as one size everywhere”直接矛盾。触发条件：同一行/同一 flex 容器里混排 outline 与其他 tone 的标签（例如列表项里 `outline` 默认标签与 `success` 状态标签并排），会出现纵向错位与宽度抖动。建议在基础规则里先声明透明边框，outline tone 只覆写颜色。
- 判断：outline tone独加0.5px border使外部尺寸相差1px属CSS事实，但没有证实当前混排视觉破坏。可统一透明边界后做截图，需保留移植来源，优先级低。
- 证据：frontend/src/upstream/Tag.module.css:7

## OCR-0006 · 部分采纳

- 位置：frontend/src/upstream/Tag.module.css:13；OCR原严重度：low
- 计划：R4-04 / P3
- 原意见：`white-space: nowrap` 没有配 `max-width`/`overflow`/`text-overflow`，标签内容（`Tag.tsx` 的 `children` 由调用方传入，可能是插件名、错误摘要等动态文案）既不会换行也不会省略，在窄容器（侧栏、表格单元格、tooltip 内的 flex 行）里会直接溢出父容器并可能压到相邻元素。触发条件：调用方传入长度不受限的文案且父容器宽度受限。建议在基础规则中加入 `max-width: 100%; overflow: hidden; text-overflow: ellipsis;`，同时约束调用方传入的标签文案长度。
- 判断：Tag nowrap无内部溢出保护，但受控调用多为短状态标签；采纳长动态状态在窄屏的截断/完整可访问名，不能假定所有调用不受限。
- 证据：frontend/src/upstream/Tag.module.css:13

## OCR-0007 · 部分采纳

- 位置：frontend/src/upstream/Input.module.css:29；OCR原严重度：medium
- 计划：R4-04 / P2
- 原意见：`.input { outline: none }` removes the keyboard-focus ring that the design system installs globally via `:focus-visible` (`upstream/theme/focus.css`, lines 10–13). Focus is then signalled only by the 0.5px wrapper border switching to `--dsw-alias-state-business-primary`; on a hairline border this is barely perceptible, and in forced-colors/high-contrast mode or when the token is overridden it can disappear entirely, leaving keyboard users with no focus indicator. Keep the input's outline suppressed but paint the project's standard ring on the wrapper for keyboard focus (`:has(.input:focus-visible)`), so pointer modality suppression from `focus.css` still applies.
- 判断：Input outline:none仅靠0.5px focus-within边框且placeholder使用dimmed低强调token。采纳实际键盘/高对比/placeholder对比度验证及适配层修正；OCR未提供本机截图测量，不直接照抄其各引擎对比度数字。
- 证据：frontend/src/upstream/Input.module.css:29

## OCR-0008 · 部分采纳

- 位置：frontend/src/upstream/Input.module.css:36；OCR原严重度：medium
- 计划：R4-04 / P2
- 原意见：`::placeholder` only sets `color`, but Firefox's UA stylesheet adds `opacity: 0.54` (and WebKit variants differ), so the placeholder is rendered partly transparent on top of an already very low-contrast token. In the light theme `--dsw-alias-label-dimmed` resolves to `rgb(225, 229, 238)` on `rgb(255, 255, 255)` (~1.2:1) and in dark to `rgb(67, 69, 74)` on `rgb(35, 35, 36)` (~1.7:1), so placeholder text is effectively unreadable. Add `opacity: 1` to normalise browsers and use a stronger label token for placeholder text.
- 判断：Input outline:none仅靠0.5px focus-within边框且placeholder使用dimmed低强调token。采纳实际键盘/高对比/placeholder对比度验证及适配层修正；OCR未提供本机截图测量，不直接照抄其各引擎对比度数字。
- 证据：frontend/src/upstream/Input.module.css:36

## OCR-0009 · 部分采纳

- 位置：frontend/src/upstream/Input.module.css:33；OCR原严重度：low
- 计划：R4-04 / P3
- 原意见：The component forwards all native input attributes (`InputHTMLAttributes`, including `disabled`) to the `<input>`, but this stylesheet has no disabled state, so a disabled field renders visually identical to an enabled one and users get no affordance that it cannot be edited. Add a disabled rule for the shared atom (muted label/border and `cursor: not-allowed`) so every consumer gets consistent behaviour.
- 判断：Input原生disabled生效但wrapper没有状态视觉，采纳一致禁用提示，与2301合并，不是能编辑禁用字段。
- 证据：frontend/src/upstream/Input.module.css:33

## OCR-0010 · 部分采纳

- 位置：frontend/src/upstream/SegmentedTabs.module.css:43；OCR原严重度：medium
- 计划：R4-04 / P2
- 原意见：这里用 `outline: none` 关闭了全局 focus.css 继承下来的 2px 焦点环（`.tab:focus-visible` 特指度更高，会覆盖 `:focus-visible`），只保留 1px 文本下划线作为唯一键盘焦点提示。标签类型是 `ReactNode`（见 SegmentedTabs.tsx 的 `label`），一旦调用方传入图标/非文本或带自身 `text-decoration` 的元素，键盘焦点就完全不可见；即使纯文本，1px 下划线在 `--dsw-alias-bg-layer-3` 背景和 600 字重选中态下对比度也明显弱于仓库既有约定（theme/focus.css、SidebarRoot.module.css 的 `.panelRow:focus-visible` 都用内缩 ring）。建议改为与设计系统一致的内缩焦点环。
- 判断：SegmentedTabs focus-visible以underline替代outline，当前纯文本仍有指示；图标label无法可靠显示underline。采纳统一可见焦点和forced-colors检查，不宣称当前所有tab无焦点。
- 证据：frontend/src/upstream/SegmentedTabs.module.css:43

## OCR-0011 · 部分采纳

- 位置：frontend/src/upstream/SegmentedTabs.module.css:33；OCR原严重度：low
- 计划：R4-04 / P3
- 原意见：`:hover` 未用 `@media (hover: hover)` 保护，且 hover 与 `[aria-selected='true']` 共用完全相同的颜色规则。触屏设备上点击后 hover 状态会滞留：先点某个 tab，再由代码改变选中值（如 App.tsx 的 `openFile` 会 `setTab('files')`），被点过的旧 tab 仍保持 `label-primary` 主色，而指示器已移走，视觉上出现两个“已选中”项。建议把 hover 限定在支持 hover 的设备，并让 hover 与选中态有可区分的表现。
- 判断：hover与selected同色但indicator/字体粗细仍区分选中；采纳触屏hover限定和交互回归，不认定必有两个选中项。
- 证据：frontend/src/upstream/SegmentedTabs.module.css:33

## OCR-0012 · 部分采纳

- 位置：frontend/src/upstream/SegmentedTabs.module.css:23；OCR原严重度：low
- 计划：R4-04 / P2
- 原意见：tab 高度被写死为 34px，同时 grid 列由组件以 `minmax(0, 1fr)` 提供（SegmentedTabs.tsx 第 57 行），列宽可以收缩到小于内容宽度。实际使用中 3 列布局的「模型与连接」「插件与扩展」在 14px 字号下每格约需 94px，容器窄于约 290px 时 CJK 标签会在按钮内换行成两行，换行后的文字溢出 34px 盒并覆盖下方内容（`overflow` 默认 visible）。建议禁止换行并裁剪溢出，或至少改用 `min-height` 允许行高自适应。
- 判断：tab固定34px且文字可换行，窄列可超盒；采纳长中文标签/缩放时不溢出布局。当前移动布局已有外层限制，仍需针对最窄支持宽度验证。
- 证据：frontend/src/upstream/SegmentedTabs.module.css:23

## OCR-0013 · 不采纳

- 位置：frontend/src/upstream/SegmentedTabs.module.css:11；OCR原严重度：low
- 计划：无
- 原意见：指示器使用物理方向属性 `left` 定位，而组件侧用物理 `translateX(selectedIndex * 100%)` 平移（SegmentedTabs.tsx 第 62 行）。在 `dir="rtl"` 下第一个 tab 位于右侧，但指示器从最左起算并向右移动，会高亮到错误的 tab（例如选中第一项时高亮显示最后一项）。此外这里的 4px 与组件侧 `calc((100% - 8px) / n)` 是同一个内边距的重复硬编码，一旦 `.tabs` 的 padding 被传入的 `className` 或主题覆盖，指示器立刻错位。建议改成逻辑属性（`inset-inline-start`/`inset-block`）并以自定义属性共享内边距值。
- 判断：产品当前中文LTR，未承诺RTL；padding与指示条现在一致。未来RTL/主题覆写padding不构成当前定位错误。
- 证据：frontend/src/upstream/SegmentedTabs.module.css:11

## OCR-0014 · 不采纳

- 位置：frontend/src/upstream/SegmentedTabs.module.css:20；OCR原严重度：low
- 计划：无
- 原意见：`justify-content` 只对 flex/grid 容器生效，但 `.tab` 自身没有声明 `display`，这里能生效完全依赖 Pill.module.css 的 `.pill { display: inline-flex; align-items: center }`，并且 `.tab` 的 height/padding/border-radius/background/color 也需要靠样式表顺序压过 `.pill`/`.interactive:hover` 的同优先级规则。这是隐式的跨模块顺序依赖：一旦 Pill 被替换、调整 import 顺序或构建产物 CSS 顺序变化，居中会静默失效，甚至回落到 Pill 的 24px 高、999px 圆角和 layer-2 背景。建议在 `.tab` 内自包含地声明布局。
- 判断：tab有意复用Pill的inline-flex原子，CSS模块组合是当前实现契约；当前构建顺序与类规则稳定，假定替换Pill不证明现有缺陷。
- 证据：frontend/src/upstream/SegmentedTabs.module.css:21

## OCR-0015 · 部分采纳

- 位置：frontend/src/upstream/Button.module.css:23；OCR原严重度：medium
- 计划：R4-04 / P3
- 原意见：第23-25行 `.md` 与第29-34行 `.sm` 都使用固定 `height`，而 `.button` 没有 `white-space: nowrap`、`overflow: hidden` 或 `min-height`。按钮是 `inline-flex`，长文案在宽度受限的容器中（如 320px 视口下的 approval-card、侧栏）会按 `line-height: 22px` 换行成 2 行以上，内容高度超过 36px 后因 `overflow` 为默认 `visible` 直接溢出按钮背景和圆角之外，压住下方元素。触发条件：任意长标签 + 容器宽度受限。建议改用 `min-height`（`.sm` 同理改为 `min-height: 28px`），让多行时按钮自适应高度。
- 判断：固定按钮height与icon默认shrink在长标签窄容器存在可用性风险；采纳实际支持视口/长中文用例后以min-height或受控ellipsis及图标flex:none适配，未证明现有固定标签溢出。
- 证据：frontend/src/upstream/Button.module.css:24

## OCR-0016 · 部分采纳

- 位置：frontend/src/upstream/Button.module.css:71；OCR原严重度：medium
- 计划：R4-04 / P3
- 原意见：`.icon` 作为 `.button` 的 flex item 未设置 `flex-shrink: 0`，默认 `shrink: 1`。当按钮宽度受限或标签较长时，图标会被压缩到 16px 以下，导致 SVG 图标变形或被裁切。建议显式声明 `flex: none;`。
- 判断：固定按钮height与icon默认shrink在长标签窄容器存在可用性风险；采纳实际支持视口/长中文用例后以min-height或受控ellipsis及图标flex:none适配，未证明现有固定标签溢出。
- 证据：frontend/src/upstream/Button.module.css:72

## OCR-0017 · 不采纳

- 位置：frontend/src/upstream/Button.module.css:54；OCR原严重度：low
- 计划：无
- 原意见：`.outline`（第54-57行）与 `.primary`、`.toolbar` 同为单类选择器，特异性相同，因此这里重新声明 `background: transparent` 会依靠源码顺序覆盖第36-39行的 `.primary`。Button.tsx 通过 `clsx(css.button, css[variant], css[size], className)` 透传 `className`，调用方一旦追加另一个变体类，就会得到失去填充色、只剩描边的 primary 按钮；同理外部样式要覆盖变体也必须使用 `!important`（`frontend/src/style.css` 中 `.new-task` 的 `!important` 正是这种被迫覆盖的实例）。建议用数据/属性变体选择器或 `:where()` 降低基础规则特异性，确保恰好一个视觉变体生效。
- 判断：Button variant一次选一个，调用者手动追加冲突变体不属于保证；className覆写需CSS特异性是正常层叠，不是primary失效的当前调用证据。
- 证据：frontend/src/upstream/Button.module.css:55

## OCR-0018 · 部分采纳

- 位置：frontend/src/upstream/theme/corner-shape.css:24；OCR原严重度：high
- 计划：R4-04 / P3
- 原意见：该通配规则会给仓库内未遵循本文件“全圆角必须配对 corner-shape: round”约定的 50% 形状套上 superellipse(1.5)，属可复现渲染缺陷：`frontend/src/style.css:83`（`.connection-dot`，6px 圆点）与 `style.css:118`（`.composer-toolbar > button`，36px 圆按钮）只有 `border-radius: 50%`，在满足 `@supports (corner-shape: superellipse(1.5))` 的引擎上会被绘制成 squircle（圆角方块），与圆形语义不符，并与其他引擎产生跨浏览器外观差异。建议在这两处补 `corner-shape: round`（或建立统一的全圆角 opt-out 机制）后再上线该全局规则。另外注释中“corner-shape 样式规范会强制该配对”的说法不成立：规范只定义 superellipse 的绘制行为，无法在运行期阻止作者遗漏配对，仓库中已经出现遗漏。建议把措辞改为“约定、需人工/自动保证”，并补一个可执行校验（例如对 `border-radius: 50%/100%/999px` 附近的 `corner-shape` 断言，或截图视觉回归），否则新增全圆角组件时会静默变形且无人察觉。
- 判断：全局corner-shape通配会影响style.css未声明round的50%圆点/按钮；采纳适配层显式圆形opt-out及支持该属性引擎截图，保持移植来源。
- 证据：frontend/src/upstream/theme/corner-shape.css:24

## OCR-0019 · 不采纳

- 位置：frontend/src/upstream/theme/corner-shape.css:22；OCR原严重度：medium
- 计划：无
- 原意见：corner-shape 不可继承，因此组件在元素上写的 `corner-shape: round` 不会传递到它的 `::before`/`::after`；而这条伪元素通配规则仍会给这些伪元素套上 superellipse。  触发条件：任何胶囊/圆形组件（如 `frontend/src/upstream/Pill.module.css:8-9`、`Tag.module.css:7-8`）若新增带圆角的装饰性伪元素（状态点、进度轨、胶囊端帽），仅在其根元素 opt-out 不够，伪元素仍会被压成方角。当前仓库尚无此用例，但注释只写了“在声明处 opt out”，未提示伪元素必须各自重复 opt-out，建议补一句说明或在组件层用 `:where(.pill, .tag)::before/::after { corner-shape: round; }` 统一兜底。
- 判断：原意见明确当前无带圆角伪元素用例；未来新增需遵守来源约定，不增无用途通配兜底。
- 证据：frontend/src/upstream/theme/corner-shape.css:22

## OCR-0020 · 不采纳

- 位置：frontend/src/upstream/AppFrame.module.css:29；OCR原严重度：medium
- 计划：无
- 原意见：`--dsh-windows-titlebar-height` 在整个仓库中没有任何定义（全仓检索仅本文件与 `SidebarRoot.module.css` 引用它），而这里作为 `padding-top` 的取值没有回退。若运行时注入缺失或晚于首帧，`padding-top` 在 computed-value 阶段失效回退为 0，同时 `::before` 的 `height: var(--dsh-windows-titlebar-height)` 会失效回退成 `auto`（无内容 → 0），原生的 caption 拖拽带塌陷为 0，网格内容上移并落到 caption 之下；`SidebarRoot.module.css:47/94` 的 `calc((var(--dsh-windows-titlebar-height) - 28px) / 2)` 则会整条失效变成 `top: auto`，固定定位的折叠按钮错位。建议为所有引用点补回退值，或在一个基础规则里声明该变量的默认值。
- 判断：App没有发布data-platform/windows-titlebar/fullscreen/sidebar-collapsed等原生属性；这些为保留的上游Desktop CSS分支，当前Web未启用，M11尚未开始。不应为未激活分支修改原样来源；Desktop接入时建立完整属性契约。
- 证据：frontend/src/upstream/AppFrame.module.css:29

## OCR-0021 · 不采纳

- 位置：frontend/src/upstream/AppFrame.module.css:43；OCR原严重度：medium
- 计划：无
- 原意见：Windows 全屏态与本文件的其它声明不一致：第 104-107 行在 `html[data-windows-titlebar][data-fullscreen]` 下把 `--dsh-frame-chrome-top` 置为 0（即认定全屏时 caption 行已不存在），但 `.frame` 依旧按 titlebar 高度留出 `padding-top`，并且这个全宽的 `::before` 仍然渲染一条 `-webkit-app-region: drag` 的不可点击拖拽带。结果是全屏时顶部留下一条既无原生 caption 可拖拽、又会吞掉指针事件的死区（内容整体下移、拖拽区拦截点击/悬停），与同文件对全屏的假设矛盾。建议在全屏分支同时把该内边距归零并隐藏 `::before`。
- 判断：App没有发布data-platform/windows-titlebar/fullscreen/sidebar-collapsed等原生属性；这些为保留的上游Desktop CSS分支，当前Web未启用，M11尚未开始。不应为未激活分支修改原样来源；Desktop接入时建立完整属性契约。
- 证据：frontend/src/upstream/AppFrame.module.css:43

## OCR-0022 · 不采纳

- 位置：frontend/src/upstream/AppFrame.module.css:88；OCR原严重度：medium
- 计划：无
- 原意见：`--dsh-frame-chrome-top` 只出现在 Windows 基础规则（第 95 行）和两个平台的全屏分支（第 107 行），非全屏的 macOS 从未定义它，而同级的 `--dsh-frame-top-clearance` 在 darwin 下是有 48px 默认值的。任何消费 `var(--dsh-frame-chrome-top)` 的样式在 macOS 非全屏场景都会拿到无效值（IACVT，属性回退到初始值），隐藏的依赖关系容易在下游表现为“顶部间距在 Windows/全屏时正常、macOS 下失效”。建议在 darwin 的基础全局规则里显式给出该变量的值。
- 判断：App没有发布data-platform/windows-titlebar/fullscreen/sidebar-collapsed等原生属性；这些为保留的上游Desktop CSS分支，当前Web未启用，M11尚未开始。不应为未激活分支修改原样来源；Desktop接入时建立完整属性契约。
- 证据：frontend/src/upstream/AppFrame.module.css:88

## OCR-0023 · 不采纳

- 位置：frontend/src/upstream/AppFrame.module.css:226；OCR原严重度：low
- 计划：无
- 原意见：拖拽手柄用 `top: 0; bottom: 0` 贯穿整个 frame 高度，因此也覆盖了 Windows 的 caption 带和 darwin 的顶部 chrome 带。由于 `.frame::before`（caption 拖拽区）没有 z-index，而手柄是 `z-index: 11` 的定位元素，手柄在 caption 带内位于拖拽层之上；同时 `-webkit-app-region` 不继承、初始值为 `no-drag`，手柄也没有声明 `drag`，于是每个列边界处都在 caption 上挖出一条 8px 的不可拖拽窗口条（光标还会变成 `col-resize`），用户从标题栏拖动窗口时在该位置会变成列宽拖拽。修复时注意不要把 `-webkit-app-region: drag` 加在整条手柄上——drag 区域不接收指针事件，加在 `.handle` 上会让列宽拖拽整体失效。正确做法是缩短手柄几何，让它从 chrome 带下方开始（darwin 顶部不透明无拖拽语义时用 `top: var(--dsh-frame-top-clearance)`，Windows 用 `var(--dsh-windows-titlebar-height, 32px)`），只把 caption/chrome 那一小段留给窗口拖拽。
- 判断：App没有发布data-platform/windows-titlebar/fullscreen/sidebar-collapsed等原生属性；这些为保留的上游Desktop CSS分支，当前Web未启用，M11尚未开始。不应为未激活分支修改原样来源；Desktop接入时建立完整属性契约。
- 证据：frontend/src/upstream/AppFrame.module.css:228

## OCR-0024 · 不采纳

- 位置：frontend/src/upstream/theme/base.css:36；OCR原严重度：high
- 计划：无
- 原意见：第 36-38 行把 `outline` 置为 `none`，而第二个选择器 `[data-dsh-automatic-focus]:focus:focus-visible`（特异度 0,3,0）也匹配键盘聚焦，且高于 focus.css 中 `:focus-visible`（0,1,0）的规则；focus.css 只补 `outline-color/width`、不设置 `outline-style`，无法抵消这里的 `none`。触发条件：`data-dsh-automatic-focus` 落在按钮/输入等可聚焦控件上（或自动聚焦后属性未移除），此时 Tab 聚焦与 Windows 高对比度模式都不会绘制任何焦点指示，违反 WCAG 2.4.7。建议只抑制非键盘聚焦，保留 `:focus-visible` 的焦点环（或为 `:focus-visible` 提供 box-shadow 等替代指示）。
- 判断：data-dsh-automatic-focus和字体自定义写入方在当前Web不存在；上游保留规则未激活。空字体/CSS注入/自动聚焦残留是未来壳层假设，无当前用户路径。
- 证据：frontend/src/upstream/theme/base.css:36

## OCR-0025 · 不采纳

- 位置：frontend/src/upstream/theme/base.css:30；OCR原严重度：medium
- 计划：无
- 原意见：第 30-31 行把用户字体列表 `--dsh-font-family-text`/`--dsh-font-family-code` 原样拼到 `font-family` 栈前面，而 `var()` 的 fallback 只在自定义属性"未定义"时生效，注释中"An unset list falls back"并不覆盖值为空的情况。触发条件：用户保存的列表为空/仅空白、以逗号结尾（如 `"My Font,"`）或引号不配对时，展开结果会变成 `, <默认栈>` 或出现连续逗号，该自定义属性在 computed-value 阶段变为 invalid，所有后代文本退回浏览器默认字体（通常为 serif），等宽字体同样受影响；此外若 boot script/ThemePresenter 通过 style 字符串拼接写入，值中的 `;`/`}` 还可能造成 CSS 注入。建议在写入方做规范化校验（trim、去掉结尾逗号、校验引号/括号配对），清空时使用 `removeProperty` 而不是写入空串或空白，而不是依赖 CSS 端兜底。
- 判断：data-dsh-automatic-focus和字体自定义写入方在当前Web不存在；上游保留规则未激活。空字体/CSS注入/自动聚焦残留是未来壳层假设，无当前用户路径。
- 证据：frontend/src/upstream/theme/base.css:30

## OCR-0026 · 不采纳

- 位置：frontend/src/upstream/TextShimmer.module.css:28；OCR原严重度：medium
- 计划：无
- 原意见：`overflow: clip` 是唯一的溢出裁剪手段，但它只在新版引擎（Chrome 90+/Safari 16+/Firefox 81+）才被支持。若目标环境不支持，该声明被整条丢弃，`.sweep` 的 `transform: translateX(±100%)` 溢出将计入最近滚动容器的可滚动区域，导致父级（如 `.activity-footer` 所在的时间线/页面）出现横向滚动条。建议在其前补一条 `overflow: hidden` 作为回退，`clip` 生效时再覆盖（`hidden` 只会裁剪本元素，不会放大祖先滚动区）。
- 判断：当前目标为已列验收的现代Chromium/Firefox/WebKit，意见针对旧WebView/旧Safari缺overflow:clip、mask、color-mix支持；无该旧版本兼容承诺，不增加未使用降级分支。
- 证据：frontend/src/upstream/TextShimmer.module.css:28

## OCR-0027 · 不采纳

- 位置：frontend/src/upstream/TextShimmer.module.css:39；OCR原严重度：medium
- 计划：无
- 原意见：`mask-image` 未提供 `-webkit-mask-image` 前缀。未加前缀的 `mask-image` 直到 Chrome 120 / Safari 15.4 才被支持，在更旧的 WebView/Electron 或旧版 Safari 上该声明会被忽略，`.sweep` 失去遮罩后整段重复文本会以 `--dsw-alias-label-shimmer` 颜色整片显示（而不是窄带高亮），与基线文本叠加后观感变成整行变色闪烁。建议同时声明前缀版本，前缀不生效时浏览器会自动忽略。
- 判断：当前目标为已列验收的现代Chromium/Firefox/WebKit，意见针对旧WebView/旧Safari缺overflow:clip、mask、color-mix支持；无该旧版本兼容承诺，不增加未使用降级分支。
- 证据：frontend/src/upstream/TextShimmer.module.css:39

## OCR-0028 · 不采纳

- 位置：frontend/src/upstream/TextShimmer.module.css:38；OCR原严重度：low
- 计划：无
- 原意见：`--dsw-alias-label-shimmer` 未提供回退值。该 token 目前定义在 `theme/design-platform.css` 中，但若某个主题/局部作用域未定义它（或定义被覆盖为空），`color: var(...)` 在计算值阶段无效并回退为继承色，`.sweep` 及其 `[data-shimmer-decoration]` 的 `background: currentColor` 也随之退化为透明，shimmer 会静默失效且没有任何视觉提示，排查成本较高。建议给出显式回退色。
- 判断：主题在main.tsx固定加载，相关token在受管主题中定义；假设主题被删/覆盖非法值或隔离组件不是当前支持调用。每个var增加fallback无实际缺失证据，不批量改上游。
- 证据：frontend/src/upstream/TextShimmer.module.css:38

## OCR-0029 · 不采纳

- 位置：frontend/src/upstream/SidebarRoot.module.css:449；OCR原严重度：high
- 计划：无
- 原意见：深色 macOS 下折叠态 New Session 的样式会被更高特异性的规则覆盖。触发条件：`data-platform='darwin'` + `data-ds-dark-theme` + 侧栏折叠（`.collapsed`）。此时 `[data-platform='darwin'] [data-ds-dark-theme] .newSession`（特异性 0,3,0）与其 `:hover` 版本（0,4,0）都高于此处的 `.collapsed .newSession`（0,2,0）与 `.collapsed .newSession:hover`（0,3,0），所以第 446-459 行设置的 `background: transparent` / hover token 全部失效：rail 上的按钮仍渲染成 `rgb(255 255 255 / 0.15)` 的半透明白色板，hover 时变成 `0.2`，与注释中“collapsed 时渲染为 rail 的普通图标控件”的设计不符。浅色主题因两条规则特异性相同、且此规则在后而侥幸正确，属于隐性顺序依赖。建议为折叠态显式补齐 darwin 条件下的选择器（放在 `.collapsed .newSession:hover` 之后即可）。
- 判断：App没有发布data-platform/windows-titlebar/fullscreen/sidebar-collapsed等原生属性；这些为保留的上游Desktop CSS分支，当前Web未启用，M11尚未开始。不应为未激活分支修改原样来源；Desktop接入时建立完整属性契约。
- 证据：frontend/src/upstream/SidebarRoot.module.css:449

## OCR-0030 · 不采纳

- 位置：frontend/src/upstream/SidebarRoot.module.css:135；OCR原严重度：medium
- 计划：无
- 原意见：折叠淡出期间控件仍可交互。`.fading > *` 只把 `opacity` 降到 0，元素依然参与命中测试与键盘 Tab 序列：触发条件是在 150ms 淡出窗口内点击侧栏（New Session、`.panelRow` 等）或按 Tab，事件会在“正在消失”的控件上触发，焦点也可能落在不可见控件上。建议同时关闭指针命中，并由 SidebarRoot 在 settle 前用 `inert`（或 `visibility: hidden` + `transition-delay`）把子树移出交互/可达树。
- 判断：App没有发布data-platform/windows-titlebar/fullscreen/sidebar-collapsed等原生属性；这些为保留的上游Desktop CSS分支，当前Web未启用，M11尚未开始。不应为未激活分支修改原样来源；Desktop接入时建立完整属性契约。
- 证据：frontend/src/upstream/SidebarRoot.module.css:135

## OCR-0031 · 不采纳

- 位置：frontend/src/upstream/SidebarRoot.module.css:358；OCR原严重度：medium
- 计划：无
- 原意见：折叠 rail 的“展开”可发现性只在 hover 时出现：`.collapsed .toggle:hover .panelIcon { display: inline }` / `.collapsed .toggle:hover .railMark { display: none }` 没有 `:focus-visible` 分支，也没有触摸替代。触发条件：键盘 Tab 到折叠态 toggle（只看到装饰性的鲸鱼标记，看不到展开图标），或在触摸/无 hover 设备上（图标永远不切换）。Windows 分支（第 116-118 行）反而强制 `display: inline`，说明两种形态不一致。建议把 `:hover` 扩展为 `:is(:hover, :focus-visible)`（触摸端另加 `:active` 或常显）。
- 判断：App没有发布data-platform/windows-titlebar/fullscreen/sidebar-collapsed等原生属性；这些为保留的上游Desktop CSS分支，当前Web未启用，M11尚未开始。不应为未激活分支修改原样来源；Desktop接入时建立完整属性契约。
- 证据：frontend/src/upstream/SidebarRoot.module.css:358

## OCR-0032 · 不采纳

- 位置：frontend/src/upstream/SidebarRoot.module.css:88；OCR原严重度：low
- 计划：无
- 原意见：折叠状态的判定方式与 AppFrame 不一致，存在静默失配风险。本文件用 `:has([data-sidebar-collapsed='true'])` 精确匹配属性值，而 AppFrame.module.css 第 168/178/187 行用的是存在性选择器 `[data-sidebar-collapsed]`。触发条件：若壳层按 React 常见写法在展开态也输出 `data-sidebar-collapsed="false"`，则 AppFrame 的规则会在展开态误命中，而这里的 `--dsh-windows-menu-start: 84px` 永不生效；反之若只写属性不赋值（`data-sidebar-collapsed=""`），这里也永不匹配。建议统一为同一种约定（存在即折叠，或在两处都校验 `="true"`）。
- 判断：App没有发布data-platform/windows-titlebar/fullscreen/sidebar-collapsed等原生属性；这些为保留的上游Desktop CSS分支，当前Web未启用，M11尚未开始。不应为未激活分支修改原样来源；Desktop接入时建立完整属性契约。
- 证据：frontend/src/upstream/SidebarRoot.module.css:88

## OCR-0033 · 采纳

- 位置：frontend/src/style.css:297；OCR原严重度：high
- 计划：R4-01 / P2
- 原意见：桌面宽度（>760px）展开态下“收起输入区”按钮被 `display:none!important` 永久隐藏，无法再折叠（单向开关）。  触发条件：窗口宽度 >760px，`App.tsx` 中 `turn-read-toggle` 的 class 仅在 `inputCollapsed` 为 true 时才追加 `collapsed`；展开后 class 变回 `turn-read-toggle`，命中本条 `display:none!important`，而唯一的展开态提示文案就是“收起输入区（保留草稿）”，页面上不存在其它折叠控件。结果：用户点一次展开后无法回到折叠态（草稿区永久占据 44vh），且折叠态切到桌面宽度后按钮反而出现，状态机方向与预期相反。  建议：不要在基础规则里隐藏该按钮，或仅在“确实另有折叠入口”时隐藏；如需为小屏保留 44px 触控高度，把它放进 `@media` 中做尺寸覆盖即可。
- 判断：style.css基础display:none!important仅collapsed或<=760覆盖；桌面初始展开无收起入口，小屏收起后桌面可展开却不能再收起。应让同一控件双向可用并验证草稿保持。
- 证据：frontend/src/style.css:297

## OCR-0034 · 不采纳

- 位置：frontend/src/style.css:0；OCR原严重度：medium
- 计划：无
- 原意见：≤1150px 时 `.inspector` 变为 `position: fixed` 的右侧浮层，但样式层没有任何遮罩/焦点隔离约束：被覆盖的 `.timeline`、`.composer`、`.topbar` 中未被面板遮住的区域仍然可点击、可聚焦。  触发条件：视口宽度在 761–1150px 之间点击顶栏“任务详情”打开浮层后，Tab 焦点仍能进入面板背后的时间线、输入框与发送按钮；用鼠标点击面板左侧未覆盖的内容会直接操作底层会话（例如发送消息），而此时用户以为自己在阅读详情。面板自身在 `.inspector-heading` 内有“关闭任务详情”按钮，因此增加遮罩不会造成无法关闭。  建议：为浮层态补一层铺满主内容区的遮罩（并让主内容不可交互，如组件侧对该区域设置 `inert`/`aria-hidden`），至少在此基础上再谈焦点循环。
- 判断：inspector为非模态详情侧面板，允许同时读时间线/编辑是当前三列工作台行为，浮层定位不自动成为modal。强加遮罩/inert会改变产品交互，未有需要模态的契约证据。
- 证据：frontend/src/style.css:196

## OCR-0035 · 部分采纳

- 位置：frontend/src/upstream/theme/focus.css:18；OCR原严重度：medium
- 计划：R4-04 / P3
- 原意见：该规则的触发条件在本仓库中永远不成立：全仓检索 `data-input-modality` 只在本文件出现（无 TS/TSX、无脚本、无 workbench 代码写入该属性），注释里引用的 `ui-primitives/input-modality` 也没有随 vendored 代码一起引入。结果是这条“指针模态下非可编辑控件不显示焦点环”的规则实际是死代码，宣称的指针/键盘差异行为并不存在，后续维护者会误以为拦截已生效。  另外两处健壮性缺陷（一旦有人补上发布方就会暴露）： 1. 选择器把属性写死在 `html` 上，若发布方按常见做法把属性写到 `body`（或 `#root`）上，规则会静默失效，没有任何报错或测试兜底； 2. 后代组合符 `body ` 使 `body` 自身（带 tabindex 时）与 shadow root 内的焦点元素逃逸该规则，行为不一致。  建议：要么随代码补上属性发布方并加一条浏览器/单测断言，要么去掉对 `html` 挂载点的硬依赖（用 `[data-input-modality='pointer'] :focus-visible:not(:read-write)`），避免依赖一个不存在的约定。
- 判断：data-input-modality仅CSS无发布方，指针抑制分支未激活；浏览器原生focus-visible仍区分部分模态。仅采纳清楚记录当前范围或按真实需求补发布方，非所有焦点行为失效。
- 证据：frontend/src/upstream/theme/focus.css:18

## OCR-0036 · 不采纳

- 位置：frontend/src/upstream/theme/focus.css:12；OCR原严重度：low
- 计划：无
- 原意见：宽度变量缺少字面回退值。按 CSS 变量规范，`--dsw-focus-ring-width` 一旦被主题层/插件覆盖为非法值或 `initial`，或本文件在被隔离的作用域中使用（没有匹配到该 `:root` 规则），`outline-width` 就属于 invalid at computed-value time，非继承属性回落到初始值 `medium`（3px），与注释里“standard geometry”的描述不符，焦点环会突然变粗一圈。 同理第 11 行的回退链末端停在另一个 CSS 变量上，没有字面颜色兜底；如果精简主题或主题插件没有加载 `design-platform.css` 中的 `--dsw-alias-state-business-primary`，`outline-color` 会计算成 `currentColor`，焦点环颜色随正文文字色变化（在部分暗色组件上几乎不可见）。
- 判断：主题在main.tsx固定加载，相关token在受管主题中定义；假设主题被删/覆盖非法值或隔离组件不是当前支持调用。每个var增加fallback无实际缺失证据，不批量改上游。
- 证据：frontend/src/upstream/theme/focus.css:12

## OCR-0037 · 不采纳

- 位置：frontend/src/upstream/theme/design-platform.css:147；OCR原严重度：medium
- 计划：无
- 原意见：静态色板在暗色块中被整体重复声明，且与亮色块不一致：`--dsw-static-neutral-bluish-60` 亮色为 rgb(245,246,247)，暗色块第 147 行写成 rgb(249,250,251)，恰好等于 bluish-50。`--dsw-static-*` 按定义应与主题无关：任何在暗色别名块中未被单独覆盖、直接引用该静态色的别名或组件会在暗色下拿到近白色，背景类用法与亮色语义相反（当前 `--dsw-alias-markdown-placeholder`、`--dsw-specific-selector`、`--dsw-specific-tip` 只是恰好都在暗色块里另行覆盖才未暴露）。更普遍的问题是亮/暗两套别名（第 166–282 行与第 284–398 行）完全手工复制：若新增别名漏写暗色版本，会静默继承亮色值。建议删除 `body[data-ds-dark-theme]` 中重复的静态色板（第 86–163 行）并统一为亮色值；同时增加自动化校验（构建期脚本或单测），解析两个块并断言键集合一致、`--dsw-static-*` 两侧取值完全相同，防止后续回归。
- 判断：static token和invert命名看似可疑但当前消费者缺失或有主题别名覆写；原意见也承认无invert消费方，不能凭语义命名擅改上游色板。
- 证据：frontend/src/upstream/theme/design-platform.css:147

## OCR-0038 · 不采纳

- 位置：frontend/src/upstream/theme/design-platform.css:190；OCR原严重度：low
- 计划：无
- 原意见：`--dsw-alias-brand-primary-invert` 与同块下一行 `--dsw-alias-brand-primary` 取值完全相同（暗色块第 308、309 行同样相同），invert 语义丢失。触发条件：组件以 `background: var(--dsw-alias-brand-primary)` 铺品牌底色、并用 `color: var(--dsw-alias-brand-primary-invert)` 写前景时，两者同色导致前景文字完全不可读。当前仓库内暂无消费方，属潜在缺陷；建议核对 Figma 源值后修正为真正的反色，或删除该未使用 token。
- 判断：static token和invert命名看似可疑但当前消费者缺失或有主题别名覆写；原意见也承认无invert消费方，不能凭语义命名擅改上游色板。
- 证据：frontend/src/upstream/theme/design-platform.css:190

## OCR-0039 · 不采纳

- 位置：frontend/src/upstream/theme/design-platform.css:402；OCR原严重度：medium
- 计划：无
- 原意见：全仓库检索 `data-platform` 只命中 CSS 选择器，没有任何 TS/HTML/原生代码把该属性写到 `html` 或 `body`（`frontend/index.html` 无内联脚本，`App.tsx` 只切换 `data-ds-dark-theme`，frontend 内也没有 `dataset`/`setAttribute` 调用）。触发条件：macOS 运行时 `html` 上不存在 `data-platform="darwin"`，第 402–409 行的兜底规则永不命中，`--dsw-specific-menu` 仍停在 58%/45% 半透明，正是注释所述『无 MenuSurface 实底背衬时模糊不可靠』的场景。建议在启动阶段（如 main.tsx）写入 `document.documentElement.dataset.platform`；同时确认原生层不是把该属性写到 body，否则 `html[...] body` 依然匹配不到。
- 判断：App没有发布data-platform/windows-titlebar/fullscreen/sidebar-collapsed等原生属性；这些为保留的上游Desktop CSS分支，当前Web未启用，M11尚未开始。不应为未激活分支修改原样来源；Desktop接入时建立完整属性契约。
- 证据：frontend/src/upstream/theme/design-platform.css:402

## OCR-0040 · 不采纳

- 位置：frontend/src/upstream/theme/design-platform.css:169；OCR原严重度：low
- 计划：无
- 原意见：这些别名直接使用 `color-mix()` 且无回退（第 169、215、216、224、264、287、333、334、342、382 行）。触发条件：在不支持 `color-mix()` 的内嵌 WebView/引擎（WebKit < 16.2、Chromium < 111 等）中，自定义属性本身可解析，但代入 `background`/`color` 时成为 guaranteed-invalid，整条声明失效——文档选中高亮、deep-diving/shimmer 文本、tooltip 快捷键底色会静默变透明。建议为每个 `color-mix()` 声明前补一条等价的 rgba 回退。
- 判断：当前目标为已列验收的现代Chromium/Firefox/WebKit，意见针对旧WebView/旧Safari缺overflow:clip、mask、color-mix支持；无该旧版本兼容承诺，不增加未使用降级分支。
- 证据：frontend/src/upstream/theme/design-platform.css:169

## OCR-0041 · 部分采纳

- 位置：frontend/src/upstream/theme/scrollbar.css:56；OCR原严重度：high
- 计划：R4-04 / P3
- 原意见：根（视口）滚动条在两个分支上都没有被主题化。触发条件：文档内容溢出视口或浏览器放大使 html 成为滚动容器。标准分支里 scrollbar-color 只有声明在根元素上才会作用于视口，body 上的声明不会传播到视口（本文件 5-9 行也承认这一点），因此 Firefox 的视口滚动条仍是 UA 浅色样式。WebKit 分支更严重：::-webkit-scrollbar* 虽然会匹配根元素，但 --dsh-scrollbar-thumb / --dsh-scrollbar-thumb-hover（17-18 行）和 --dsh-scrollbar-width（25 行）只声明在 body，自定义属性不向上继承，html 上这些 var() 都是 guaranteed-invalid，于是 background 取初始值 transparent，track/corner 也是透明的——视口滚动条变成一条完全不可见的自定义滚动条（比 UA 默认更糟，用户无法感知页面可滚动）。另外深色 token 只挂在 body[data-ds-dark-theme]（design-platform.css 284 行），即使把 html 加进选择器也取不到深色值。建议把 --dsh-* 声明提升到 :root（深色 token 同步显式提升到 html/根级主题挂点），或至少在 html 上按 [data-ds-dark-theme] 给出字面量回退，并把 html 加入标准分支的选择器列表。
- 判断：scrollbar局部变量只在body而root不继承，视口溢出场景可能退化；App主要滚动容器内部受控。采纳浏览器缩放/根溢出下的可见滑块验证和适配层root兜底，不宣称当前全部滚动条不可见。
- 证据：frontend/src/upstream/theme/scrollbar.css:56

## OCR-0042 · 不采纳

- 位置：frontend/src/upstream/theme/scrollbar.css:83；OCR原严重度：medium
- 计划：无
- 原意见：缺少字面量兜底：--dsh-scrollbar-thumb 声明为 var(--dsw-alias-scrollbar-bg-l1)（18 行同理），一旦该 alias token 未定义（样式表加载顺序变化、token 改名、或变量被 shadow root 隔离），自定义属性在计算值阶段即为 guaranteed-invalid，这里 background 退化为初始值 transparent，滑块完全不可见；hover 规则（88 行）同理。标准分支退化后是 `auto`（仍可用），WebKit 分支的退化结果反而更差。建议在使用点提供兜底颜色（对 guaranteed-invalid 的自定义属性，var() 的第二参数会生效）。
- 判断：主题在main.tsx固定加载，相关token在受管主题中定义；假设主题被删/覆盖非法值或隔离组件不是当前支持调用。每个var增加fallback无实际缺失证据，不批量改上游。
- 证据：frontend/src/upstream/theme/scrollbar.css:83

## OCR-0043 · 不采纳

- 位置：frontend/src/upstream/theme/scrollbar.css:25；OCR原严重度：medium
- 计划：无
- 原意见：跨引擎几何契约不一致：--dsh-scrollbar-width 只在 WebKit 分支（68-69 行）生效，Firefox 走 scrollbar-width: thin，实际宽度由平台决定（通常明显大于 5px）；同时 --dsh-scrollbar-thumb-border / --dsh-scrollbar-track-margin 在标准分支被完全忽略。任何按 21-24 行承诺读取该变量来为滚动条预留空间的布局（当前仓库内没有读取方，注释提到的 ConversationRoot 也不存在）在 Firefox 下会横向错位数像素，并且 Firefox 下无法通过 rebind 实现内缩。建议把该变量标注为 WebKit-only，或让消费方按同一 @supports 条件分支处理（Firefox 用 scrollbar-gutter/平台宽度兜底）。
- 判断：当前无按变量精确预留Firefox滚动条空间消费方，track-margin默认0；跨引擎平台thin和未来rebind横向语义不证明现有布局错误。
- 证据：frontend/src/upstream/theme/scrollbar.css:25

## OCR-0044 · 不采纳

- 位置：frontend/src/upstream/theme/scrollbar.css:75；OCR原严重度：low
- 计划：无
- 原意见：逻辑属性在滚动条部件上不会随滚动方向旋转：垂直滚动条上 margin-block 作用在轨道两端，横向滚动条上则作用在轨道厚度（上下），同一个 --dsh-scrollbar-track-margin 在两种方向下语义相反。默认 0px 时无影响，但一旦按 11-14 行的契约在滚动容器上 rebind（例如为两端留 2px），横向滚动条会变成“变细”而不是“内缩”，与垂直滚动条表现不一致。若要表达两端内缩，应针对方向分别使用 margin-inline（横向条）/ margin-block（纵向条），或改用统一的 margin。
- 判断：当前无按变量精确预留Firefox滚动条空间消费方，track-margin默认0；跨引擎平台thin和未来rebind横向语义不证明现有布局错误。
- 证据：frontend/src/upstream/theme/scrollbar.css:75

## OCR-0045 · 不采纳

- 位置：examples/broken-project/src/main/java/example/Calculator.java:4；OCR原严重度：critical
- 计划：无
- 原意见：This is a hard compilation error: the binary `+` operator is missing its right-hand operand, so `Calculator.java` cannot compile and the whole `broken-project` module (and any downstream target depending on `example.Calculator`) will fail to build. Fix the expression to `a + b`.
- 判断：broken-project是故意语法错误的修复夹具，calculator是普通int加法最小基准；修正语法、加入addExact或扩展夹具测试会改变任务，不属于当前产品缺陷。证据还包括scripts/live-evaluate.py的fixture与M10.5.9开发流程契约。
- 证据：examples/broken-project/src/main/java/example/Calculator.java:4

## OCR-0046 · 不采纳

- 位置：examples/broken-project/src/main/java/example/Calculator.java:4；OCR原严重度：low
- 计划：无
- 原意见：After the syntax error is fixed, note that plain `int` addition wraps silently on overflow for large operands, so `add(Integer.MAX_VALUE, 1)` returns a negative number with no signal. Since there is no caller/test evidence of the intended contract in this file, decide explicitly: keep wrapping semantics (and document it) or use `Math.addExact(a, b)` so overflow surfaces as `ArithmeticException` instead of a silent wrong result.
- 判断：broken-project是故意语法错误的修复夹具，calculator是普通int加法最小基准；修正语法、加入addExact或扩展夹具测试会改变任务，不属于当前产品缺陷。证据还包括scripts/live-evaluate.py的fixture与M10.5.9开发流程契约。
- 证据：examples/broken-project/src/main/java/example/Calculator.java:4

## OCR-0047 · 不采纳

- 位置：examples/calculator/src/main/java/example/Calculator.java:4；OCR原严重度：low
- 计划：无
- 原意见：第 4 行的 `add` 直接使用 int 相加，没有溢出校验：当 a、b 同号且结果超出 int 范围（如 Integer.MAX_VALUE + 1）时会静默回绕为负数，调用方拿到错误结果且无任何异常提示。若该方法被视为对外契约（脚本 scripts/live-evaluate.py 已通过反射调用 `add(int,int)`），建议改用 `Math.addExact(a, b)` 或 `long` 返回类型，让溢出显式暴露；若仅为测试夹具并有意保持最小实现，请在方法上注明该限制。
- 判断：broken-project是故意语法错误的修复夹具，calculator是普通int加法最小基准；修正语法、加入addExact或扩展夹具测试会改变任务，不属于当前产品缺陷。证据还包括scripts/live-evaluate.py的fixture与M10.5.9开发流程契约。
- 证据：examples/calculator/src/main/java/example/Calculator.java:4

## OCR-0048 · 不采纳

- 位置：examples/broken-project/src/test/java/example/CalculatorTest.java:7；OCR原严重度：low
- 计划：无
- 原意见：The only assertion is a single happy path. This test passes for any implementation that happens to return 5 for add(2, 3) — it cannot distinguish a correct `a + b` from a hard-coded return, and it leaves negative operands, zero, and int overflow (e.g. Integer.MAX_VALUE + 1) unverified. For a class meant to smoke-test a build harness, also add cases such as add(0, 0), add(-2, 3), add(Integer.MAX_VALUE, 1) so regressions in the calculation path are actually caught. Additionally, the assertion carries no failure message, so a failure gives no context about inputs.
- 判断：broken-project是故意语法错误的修复夹具，calculator是普通int加法最小基准；修正语法、加入addExact或扩展夹具测试会改变任务，不属于当前产品缺陷。证据还包括scripts/live-evaluate.py的fixture与M10.5.9开发流程契约。
- 证据：examples/broken-project/src/test/java/example/CalculatorTest.java:7

## OCR-1171 · 不采纳

- 位置：.opencodereview/rule.json:12；OCR原严重度：low
- 计划：无
- 原意见：这些排除项以仓库根为锚点（`frontend/dist/**` 同理），不会匹配嵌套路径。触发条件：在子模块/嵌套 git worktree 目录中运行审查，或存在模块级 `.build-cache`/`.dsh-tmp`/`.jharness`/`frontend/dist`（例如 `frontend/packages/x/dist`）。此时这些目录仍会被扫描，可能产出误报并浪费有界扫描配额。建议改为 `**/` 前缀的递归模式。
- 判断：规则按当前仓库根运行；嵌套worktree/额外缓存目录不是当前扫描输入契约，递归排除反会漏掉有意扫描的夹具，未见实际漏排。
- 证据：.opencodereview/rule.json:13

## OCR-1808 · 不采纳

- 位置：sdk/typescript/package.json:0；OCR原严重度：high
- 计划：无
- 原意见：导出裸 .ts 源文件作为包入口，对真实安装场景不可用：Node 的内置类型擦除明确不处理 node_modules 下的文件，因此外部消费者 `import ... from '@jharness/sdk'`（从 registry 安装后文件位于 node_modules）会以 ERR_UNKNOWN_FILE_EXTENSION / 无法加载 TypeScript 文件失败；只有 monorepo 内通过软链（realpath 指向 workspace 源码）使用时才偶然可用。此外 exports 只有字符串形式、没有 `types` 条件，使用 moduleResolution node16/nodenext 或非类型擦除打包器的消费者拿不到声明也无法解析。建议改为构建产物入口（dist/*.js + dist/*.d.ts），并显式声明 types 条件；若确实只允许仓库内消费，应在 README/包描述中明确说明并移除这个误导性的 exports。
- 判断：M10.5.9契约明确SDK为零依赖源码分发，非npm注册表包；示例直接引用../typescript/src/index.ts，private:true有意禁发。改dist可发布包属于新增分发能力。
- 证据：sdk/typescript/package.json:6

## OCR-1809 · 不采纳

- 位置：sdk/typescript/package.json:4；OCR原严重度：medium
- 计划：无
- 原意见：同时声明 scoped 名称 `@jharness/sdk`、语义化版本 1.0.0 和 `private: true`：任何 `npm publish`（含 CI 发布任务）都会直接失败，版本号也无实际意义。需要确认发布是否被有意禁用——若计划对外发布，应去掉 private 并补充 `files` 白名单（当前会把 src/ 全部内容打包）；若仅供内部使用，则带 name/version/exports 的清单会诱导他人按包名引入，而仓库内并无任何地方引用该包名、也没有 workspace 配置将其链接，实际无法解析。
- 判断：M10.5.9契约明确SDK为零依赖源码分发，非npm注册表包；示例直接引用../typescript/src/index.ts，private:true有意禁发。改dist可发布包属于新增分发能力。
- 证据：sdk/typescript/package.json:4

## OCR-1810 · 不采纳

- 位置：sdk/typescript/package.json:7；OCR原严重度：medium
- 计划：无
- 原意见：`engines` 默认只是建议性字段（除非使用 engine-strict），npm/yarn 安装时仅告警不阻止运行；而该包依赖 Node 22.18+ 的默认类型擦除，低于该版本时消费者不会看到清晰提示，只会得到难以定位的 TS 加载错误。建议同时在代码入口（如 JHarnessClient.start 前置检查）校验 process.versions.node，或在仓库 .npmrc 中开启 engine-strict，使不满足版本时立刻以明确错误失败。
- 判断：engines声明当前源码执行基线；不支持旧Node没有兼容承诺，未强制engine-strict不能证明现有SDK不可用。
- 证据：sdk/typescript/package.json:7

## OCR-1811 · 不采纳

- 位置：frontend/tsconfig.json:3；OCR原严重度：medium
- 计划：无
- 原意见：`lib` 声明为 ES2023，但 `target` 仅为 ES2022，且本项目 `noEmit: true`，实际降级由 Vite/esbuild 负责（esbuild 只做语法降级、不做 API polyfill）。结果是 `Array.prototype.findLast/toSorted`、`Object.groupBy` 等 ES2023 API 在类型层面完全合法，编译/构建均不会报错，但在 Vite 6 默认构建目标（baseline-widely-available，含 Safari 14/15 等）的浏览器上会直接抛 `TypeError: xxx is not a function`。触发条件：任何人在 src 中使用 ES2023 及以上 API。建议让 `lib` 与 `target`（或显式声明的运行时基线）保持一致，或在文档/构建目标中明确仅支持 ES2023 运行时。
- 判断：意见前提均是未来新增API、类型重导出、Node全局或嵌套测试，当前src与测试未出现所述运行错误。配置拆分可维护，但不作为已确认缺陷。
- 证据：frontend/tsconfig.json:3

## OCR-1812 · 不采纳

- 位置：frontend/tsconfig.json:13；OCR原严重度：medium
- 计划：无
- 原意见：该前端由 Vite/esbuild 按单文件独立转译（`noEmit: true`，构建走 `vite build`），但此处未开启 `isolatedModules`/`verbatimModuleSyntax`。TypeScript 5+ 的 `isolatedModules` 默认为 false，因此 `export { SomeType } from './x'`、跨文件 `const enum` 等只能在整程序语义下成立的写法可以通过 `tsc --noEmit` 类型检查，却会在 esbuild 单文件转译时产生运行期导入错误（导出被当作值导入而实际不存在）或语义差异。触发条件：src 中出现未加 `type` 修饰的类型重导出或 const enum，CI 的 `tsc --noEmit` 通过但 `vite build`/运行时失败。建议显式开启 `isolatedModules`（或 `verbatimModuleSyntax`），让类型检查与打包器语义保持一致。
- 判断：意见前提均是未来新增API、类型重导出、Node全局或嵌套测试，当前src与测试未出现所述运行错误。配置拆分可维护，但不作为已确认缺陷。
- 证据：frontend/tsconfig.json:13

## OCR-1813 · 不采纳

- 位置：frontend/tsconfig.json:14；OCR原严重度：medium
- 计划：无
- 原意见：`types: ["vite/client", "node"]` 把 Node 的全局类型引入了浏览器侧代码的同一个类型域。这样 `process`、`Buffer`、`require`、`__dirname` 等在 `src/` 中会被判定为合法（当前 src 尚未使用，属潜在缺陷），但浏览器运行时这些标识符并不存在，Vite 也不会做 polyfill（`process.env` 不注入），会得到 `ReferenceError`/`undefined` 类运行期故障，而类型检查与构建均无法拦截。建议拆分配置：浏览器侧（src）只保留 `vite/client`，Node 侧（`vite.config.ts`、`tests/*.ts`）单独建立 `tsconfig.node.json` 并保留 `node`，主 tsconfig 用 `references` 组合；若暂不拆分，也应在 code review/ESLint 层面禁止 src 使用 Node 全局。
- 判断：意见前提均是未来新增API、类型重导出、Node全局或嵌套测试，当前src与测试未出现所述运行错误。配置拆分可维护，但不作为已确认缺陷。
- 证据：frontend/tsconfig.json:14

## OCR-1814 · 不采纳

- 位置：frontend/tsconfig.json:16；OCR原严重度：medium
- 计划：无
- 原意见：`tests/*.ts` 中的 `*` 不跨越目录分隔符，只会匹配 `tests/` 直属的 .ts 文件（当前仅 `tests/client.test.ts`）。一旦按照惯例把 TS 测试放到 `tests/unit/`、`tests/integration/` 等子目录，这些文件将不在 `include` 中，`npm run build` 里的 `tsc --noEmit` 会静默跳过它们，类型错误在 CI 中不会被发现（而 `node --import tsx --test` 运行时会直接执行、不做类型检查）。建议改为递归 glob。
- 判断：意见前提均是未来新增API、类型重导出、Node全局或嵌套测试，当前src与测试未出现所述运行错误。配置拆分可维护，但不作为已确认缺陷。
- 证据：frontend/tsconfig.json:16

## OCR-1815 · 部分采纳

- 位置：sdk/examples/demo.json:2；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：demo.json 未声明 `apiKeyEnv`，因此会回落到默认环境变量 `JHARNESS_API_KEY`（见 CliConfig.key()），而该密钥会以 `Authorization: Bearer` 形式通过**明文 http** 发往 `127.0.0.1:8089`（EcosystemCli.open() → config.key() → OpenAiCompatibleLlmClient）。触发条件：开发者主机上 `JHARNESS_API_KEY` 存放的是真实 provider 凭据，同时本机 127.0.0.1:8089 已被其他进程（而非 loopback_provider.py）占用——该进程即可直接读取生产凭据；loopback socket 在同一主机上不区分用户。建议为本示例固定一个演示专用变量名，避免误用真实密钥。
- 判断：demo默认JHARNESS_API_KEY而地址固定8089，开发者手动运行可误用已有真实provider key；示例测试实际覆写synthetic-demo-key。采纳演示专用apiKeyEnv并文档同步，不认测试已泄露真实key。
- 证据：sdk/examples/demo.json:2

## OCR-1816 · 部分采纳

- 位置：sdk/examples/demo.json:5；OCR原严重度：low
- 计划：R6-03 / P2
- 原意见：配置只给了 `durationSeconds: 30`，却省略了 `requestTimeoutSeconds`，后者默认 90 秒（CliConfig.requestTimeout() 默认值 90，范围 1~300），即单次模型请求的等待上限远大于整个 turn 的时间预算；而 sdk/examples/run.py 第 14 行、run.ts 第 8 行只轮询 35 秒就退出。触发条件：provider 建立连接后不返回（如被其他本地进程占端口后 hang），单步会一直阻塞到 ~90s，客户端已超时放弃，子 JVM 仍在运行，演示会打印非终态 phase 并残留进程。对照 sdk/tests/run.mjs 第 33 行的写法（durationSeconds 60 + requestTimeoutSeconds 30），建议显式设置一个小于 `durationSeconds` 的请求超时。
- 判断：demo省略requestTimeout而轮预算30秒/示例轮询35秒，配置可更清楚设较短请求时限；示例finally/with会关闭自有JVM，并非必然残留。采纳合理预算与未完成输出。
- 证据：sdk/examples/demo.json:5

## OCR-1817 · 部分采纳

- 位置：frontend/package.json:5；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：esbuild 覆盖版本与 tsx 的依赖范围不兼容：package-lock.json 中 tsx@4.21.0 依赖 esbuild `~0.27.0`，而这里把 tsx 的 esbuild 强制改为 0.28.1，已超出其声明的 semver 范围。esbuild 在 0.x 阶段的小版本升级按惯例是破坏性的，一旦 tsx 的 loader/transform API 与 0.28 不匹配，`npm test`（`node --import tsx --test`）会直接在加载阶段失败，而这是 CI 唯一的单元测试门禁。触发条件：重新 `npm ci`（或升级 tsx 版本、esbuild 发布新的次版本）后执行 `npm test`/任何 tsx 脚本。建议改为落在 tsx 支持范围内的版本（例如 `~0.27.0` 内最新的已修复版本），或先升级 tsx 到声明支持 0.28.x 的版本再保留该覆盖，并在 CI 中实际执行一次 `npm test` 验证。
- 判断：tsx依赖范围与esbuild override确实不一致；超范围不等于已发生API故障，已有客户端验收。采纳升级声明兼容组合并保留npm ci/build/client验证。
- 证据：frontend/package.json:5

## OCR-1818 · 部分采纳

- 位置：frontend/package.json:4；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：缺少 `engines.node` 约束，运行时版本不可复现：本包依赖 Vite 6、TypeScript 6、tsx（>=18）以及 Node 内置 `--test`/`--import` 能力，CI 固定使用 Node 24，但清单未声明所需 Node 版本。触发条件：在较旧的 Node（如 16/18 早期）上 `npm install` 会成功，随后 `npm test`/`npm run build` 因 `--import tsx` 或 Vite/TS 版本要求而失败，报错表现为与本次变更无关的环境问题。建议显式声明 engines（与 CI 对齐），并配合 `engine-strict` 或在 CI 中检查，使不满足要求的 Node 在安装阶段即失败。
- 判断：前端package未声明Node engines，而CI/tsx/Vite有版本要求；补明确版本和前置诊断合理，无需默认全局engine-strict。
- 证据：frontend/package.json:4

## OCR-1819 · 部分采纳

- 位置：frontend/upstream.json:3；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：第2-4行只记录了项目名与 40 位 commit，缺少可定位的上游仓库 URL（或归档 URL）和归档 SHA-256。触发条件：审计者仅凭本清单复核时，无法确定 "DeepSeek Harness" 对应哪个仓库/归档，也就无法独立重取 d743267... 的原始文件来核验各条目 sha256；对本清单中未出现在 M10-frontend-reuse-source.json 里的文件（如 Pill/SegmentedTabs/TextShimmer、scrollbar.css、corner-shape.css 等 15 个），清单自身成为哈希的唯一来源，无法证明这些字节确实来自所声明的 commit。同仓库 docs/testing/M7-upstream-review.json 已用 archiveUrl + archiveSha256 记录同一类来源，本文件应补齐同类字段以保证可复现性。
- 判断：upstream.json仅有项目/commit/files，单独不含可重取地址；仓库已有M10来源证据，因此不是来源完全缺失。采纳补URL及归档摘要。
- 证据：frontend/upstream.json:3

## OCR-1822 · 采纳

- 位置：frontend/scripts/package-ui.mjs:24；OCR原严重度：high
- 计划：R6-01 / P2
- 原意见：Generate ui-files.txt from actual dist enumeration; assert index.html exists, reject unexpected files, and relax filename regex to allow dots while still limiting extension to js/css.
- 判断：清单固定添加index.html但未确认存在，忽略dist根额外文件，assets表达式还拒绝含点的合法chunk名。应按真实输出精确枚举与验证。
- 证据：frontend/scripts/package-ui.mjs:24

## OCR-1823 · 部分采纳

- 位置：frontend/scripts/package-ui.mjs:7；OCR原严重度：high
- 计划：R6-01 / P2
- 原意见：完整性校验的路径未做边界限制：`new URL(file.destination, root)` 会接受绝对路径（如 `/etc/passwd`）和带 `..` 的相对路径，从而读取 frontend/ 之外的任意文件；`readFile` 还会跟随符号链接，`src/upstream/*` 若被替换为指向仓库外的软链也能通过/绕过校验。同时未对结果做 realpath 归一化。建议将 destination 解析为绝对路径后校验其位于 frontend/ 根目录之下（并且不在 node_modules/dist 中），拒绝绝对路径与 `..` 段。
- 判断：new URL(destination,root)接受绝对/上跳并跟随软链；清单来自受版本控制本地仓库，不等同远程读取漏洞。采纳schema、真实路径与根边界约束。
- 证据：frontend/scripts/package-ui.mjs:7

## OCR-1824 · 采纳

- 位置：frontend/scripts/package-ui.mjs:5；OCR原严重度：high
- 计划：R6-01 / P2
- 原意见：`provenance.files` 未做结构校验：若 upstream.json 被截断、被改成 `{"files": []}` 或缺少 `files` 字段（此时 for-of 会抛 TypeError，但空数组会静默通过），哈希门禁就被完全绕过，不会报任何错。建议先校验 `Array.isArray(provenance.files)`、长度非零、每项含字符串 `destination` 和 64 位十六进制 `sha256`，并校验预期清单至少覆盖 `LICENSE-DeepSeek-Harness.txt` 与 `src/upstream/` 下的全部文件（避免清单被悄悄裁剪）。
- 判断：for-of provenance.files没有非空/完整集/唯一性约束，files=[]会完全跳过上游字节校验。应核对受管文件完整集合。
- 证据：frontend/scripts/package-ui.mjs:5

## OCR-1825 · 部分采纳

- 位置：frontend/scripts/package-ui.mjs:15；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：许可证收集依赖写死的依赖名列表，与实际打包图脱节：一旦新增/间接引入被打进 bundle 的运行时依赖（例如替换 clsx、react 引入新的 runtime shim），该依赖会被发布但许可证未随包分发，且脚本不会报错。建议从 vite 的 bundle manifest（`build.manifest`）或 `package.json` 的依赖闭包推导实际参与打包的包集合，并与硬编码列表做差集校验，缺少许可证时直接失败。
- 判断：许可证列表写死四包，无新增生产闭包差异门禁；尚未证明当前漏证。采纳将实际锁文件生产闭包与许可证集合对比。
- 证据：frontend/scripts/package-ui.mjs:15

## OCR-1826 · 部分采纳

- 位置：frontend/scripts/package-ui.mjs:23；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：写出顺序与原子性有问题：`dist/ui-license.txt` 在输出白名单校验（第 25 行）之前就被写入，若校验失败或进程崩溃，dist/ 会停留在“许可证已更新、ui-files.txt 未更新”的不一致状态；且两次 `writeFile` 都是原地覆盖，读者可能读到半截文件。建议先完成所有校验、在内存中算好内容，再通过“写临时文件 + rename”原子落盘，并在写 ui-license.txt 之前完成 ui-files.txt 的内容校验。
- 判断：ui-license先写后校验输出白名单，失败留部分构建输出；失败构建不会成功候选。采纳全部校验后临时写入和替换。
- 证据：frontend/scripts/package-ui.mjs:23

## OCR-1827 · 部分采纳

- 位置：frontend/tests/browser.mjs:15；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：`spawn` 没有注册 `error` 监听器。触发条件：`JAVA_HOME` 指向不存在的路径、缺少 java 可执行文件或 classpath 无法加载时，Node 会发出未处理的 `error` 事件并直接抛出未捕获异常，脚本既不会写失败产物也不会触发 finally 中的清理，留下已启动的浏览器/半初始化宿主。建议在 spawn 后立即监听 `error`（并记录到失败证据中，同时让后续流程快速失败）。
- 判断：实际spawn未注册error事件，坏java/python可未捕获退出且无结构化fixture失败回执；采纳error/close监听与前置诊断。classpath缺失是Java非零退出非spawn error，原意见这部分不准确。
- 证据：frontend/tests/browser.mjs:15

## OCR-1828 · 部分采纳

- 位置：frontend/tests/browser.mjs:238；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：清理路径不安全：`await browser.close()` 一旦抛错（浏览器崩溃、CDP 连接断开），后面的 `host.stdin.end()` 与 `host.kill()` 全部被跳过，Java 宿主进程会被泄漏；`host.stdin.end()` 在宿主已退出时也可能异步触发 EPIPE 的未处理 error 事件；`host.kill()` 只发 SIGTERM 且不等待 `exit`，忽略信号时进程残留；另外 try 分支第 221 行已调用过 `host.stdin.end()`，重复 end 无保护。建议嵌套 try/finally 保证三段清理互不阻断，结束 stdin 后等待 `exit`（带超时），超时再 `SIGKILL`。
- 判断：frontend/tests/browser.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：frontend/tests/browser.mjs:238

## OCR-1829 · 部分采纳

- 位置：frontend/tests/browser.mjs:16；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`hostOutput` 无界累加宿主 stdout+stderr，且失败路径会把整个 `hostOutput` 与整页 `innerText` 拼接写盘。触发条件：宿主输出大量日志（DEBUG/堆栈循环）或页面内容很大时，进程内存持续增长，失败诊断文件可达数百 MB，反而拖慢/阻断失败上报。建议对 `hostOutput` 设置上限（超出后截断并标记省略），并对写入文件的页面文本做长度截断。
- 判断：frontend/tests/browser.mjs确逐块累加输出/请求字符串，运行时间有限不等于字节有界；采纳尾部日志预算、截断标记及UTF-8连续解码，防止失败诊断本身耗尽内存。尚无当前fixture异常输出的实际OOM证据。
- 证据：frontend/tests/browser.mjs:16

## OCR-1830 · 部分采纳

- 位置：frontend/tests/browser.mjs:235；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：失败产物只对静态 fixture token 做了 `replaceAll`，未脱敏运行时捕获的动态凭据：`browserCredentials`（第 37 行从 `/v1/browser-session` 响应体提取的会话令牌）、`hostOutput`（Java 宿主 stdout/stderr，可能打印令牌/路径）以及被截取页面文本 `text`。触发条件：页面文本或宿主日志包含动态会话令牌时，`M10-browser-failure.txt` 会把凭据明文落盘。另需注意 `final` 产物把原始 `runtime`（evidence JSON）与 `browserErrors` 原样写出，同样可能夹带会话令牌。建议统一抽出一个 redact 函数，对 token + 全部 `browserCredentials` 做替换，并在写出 `final` 前对 `runtime`/`browserErrors` 也做同样处理。
- 判断：browserCredentials动态token已被采集但失败文本仅替换固定fixture token，失败回执统一脱敏集合合理。runtime当前受管schema未见实际secret，不能认已发生泄漏。
- 证据：frontend/tests/browser.mjs:235

## OCR-1831 · 部分采纳

- 位置：frontend/tests/browser.mjs:21；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：check helper defaults detail to true and assert.ok(Promise) is always true, so checks without detail always pass. Make detail required and reject Promise/Timer values; specifically assert ArrowLeft reset and bounded resize, in-flight keyword change rejection, and absence of replay controls.
- 判断：check默认true可作为前序assert/waitFor的记录，并非每个无参数check都无效；ArrowLeft恢复确未断言，应补具体几何和边界结果，拒Promise误用。不得删除已有有效前序assert。
- 证据：frontend/tests/browser.mjs:21

## OCR-1832 · 不采纳

- 位置：frontend/tests/browser.mjs:49；OCR原严重度：high
- 计划：无
- 原意见：`newTask` 只等待“存在任意被选中任务”就返回，而不是等待本次创建的任务被选中。触发条件：上一步已有 `.task-item.selected`（例如前一个 write/denied 任务仍处于选中态）时，`until` 立即通过，随后读取的 `sessionStorage['jh-selected']` 仍是上一个任务 id（极端情况下为 `null`，会拼出 `/v1/tasks/null`）。后果：后续 `ended()`、审批关联、Diff、取消、报告断言都作用在错误任务上；由于旧任务通常已是 FINISHED/VERIFIED，`ended()` 会立即通过，形成假阳性（例如“cancel routed through actual Java runtime”“Diff displays actual recorded git result”实际上验证的是旧任务）。修复：点击前记录当前选中 id，等待选中 id 变化且非空后再返回，并断言其不是旧 id。
- 判断：newTask先点击新建任务，App.tsx:277 selectTask('')清除旧selected，之后fill/click经过多次UI等待，旧.task-item.selected不保留。OCR忽略调用方清选中流程；可防御比较prior，但未证实所述复用旧id。
- 证据：frontend/tests/browser.mjs:49

## OCR-1833 · 部分采纳

- 位置：frontend/tests/browser.mjs:210；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：`boundingBox()` 在元素不可见/尚未布局时返回 `null`，这里直接解引用 `chosenBounds.x` / `indicatorBounds.width` 会抛出 TypeError（而不是给出可读的断言失败），且错误被 catch 后表现为页面上难以定位的崩溃。触发条件：移动端视口下 tab 指示器或选中 tab 恰好处于滚动容器外/动画未完成。建议先断言两个 bounds 非空，再进行比较。
- 判断：boundingBox可null且源码直接解引用，失败本就使测试非零，不会误PASS；补assert非空/定位信息改善诊断。
- 证据：frontend/tests/browser.mjs:210

## OCR-1834 · 部分采纳

- 位置：frontend/tests/extensions.mjs:12；OCR原严重度：high
- 计划：R0-03 / P3
- 原意见：第 12 行：`process.env.JAVA_HOME` 未做空值校验就直接传给 `join()`。当 JAVA_HOME 缺失时 `join(undefined, ...)` 会在模块顶层抛 TypeError，此时 try/catch（第 79 行）尚未进入，因此既不会输出 `M10.5.7-failure.txt`，也不会有任何可读的失败原因，只会打印堆栈；同理若第 173 行组装 `result` 时 `readFile(jar)` 发现 jar 不存在，会在 finally 之后抛出 ENOENT，覆盖真实结果。建议在 `start()` 内显式断言 JAVA_HOME 与 jar 存在，并给出可读错误。
- 判断：frontend/tests/extensions.mjs直接从JAVA_HOME构造JDK路径，确无缺项/文件存在前置提示；采纳清楚的fixture前置失败回执。CI已设置JDK，缺变量失败不是正常验收误通过，不采纳为高严重度产品缺陷。
- 证据：frontend/tests/extensions.mjs:12

## OCR-1835 · 部分采纳

- 位置：frontend/tests/extensions.mjs:70；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 70 行：`env: { ...process.env, ... }` 把整个 CI 环境（云凭据、令牌、代理口令等）透传给宿主，宿主再传给其 spawn 的 stdio MCP 子进程，违反环境变量白名单原则；同时这里没有给 `host` 注册 `'error'` 监听。当 `java`/jar 路径不存在时 child_process 会发出未处理的 `'error'` 事件（无人监听会直接抛异常），且 `host.exitCode` 保持 null，第 72 行的 `until(() => base || host.exitCode !== null)` 会白等满 30s 才报 `Fixture timeout`。建议只透传 PATH/JAVA_HOME/SystemRoot 等必需变量并显式传入测试令牌，同时 `host.on('error', e => { output += ... })`。
- 判断：采纳具体测试启动诊断缺口：frontend/tests/extensions.mjs的spawn/接收路径无启动error监听或按完整行解析保护，错误JDK/拆分端口可造成无结构异常。继承受信任测试宿主环境不是已证明凭据泄漏，不采纳该安全严重度。
- 证据：frontend/tests/extensions.mjs:70

## OCR-1836 · 部分采纳

- 位置：frontend/tests/extensions.mjs:22；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 22 行：假设每个模型请求都带 `tools` 数组，直接 `body.tools.map(...)`。只要宿主发出一次不带 tools 的请求（例如摘要/标题类请求）或请求体不是 JSON，异步处理器就会抛异常，而这里没有任何 try/catch，响应永远不会写回，测试只能等到 30s `Fixture timeout`，看不出真正原因。建议 `JSON.parse` 加保护、`tools` 用 `Array.isArray(body.tools) ? ... : []`，并对没有 tools 的请求显式返回 stop 而不是抛错（注意不要因此被误判成 child 请求）。
- 判断：frontend/tests/extensions.mjs的loopback合成provider确在async handler直接解析JSON，异常缺统一失败响应/回执；采纳非法请求时快速、可诊断地失败及请求字节预算。此provider仅测试替身，不是产品公网协议漏洞。
- 证据：frontend/tests/extensions.mjs:22

## OCR-1837 · 部分采纳

- 位置：frontend/tests/extensions.mjs:130；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：第 130 行：分页循环没有任何上界，也不校验游标前进。若服务端返回 `more: true` 但 `next` 为空或与上一次相同（协议实现异常），这里会无限追加事件导致内存膨胀并卡死测试。建议限制最大页数（例如 limit/page 数）并校验 `value.next && value.next !== after`，否则抛错。
- 判断：各events/history helper循环只按游标/more继续，无全局截止/重复cursor集合，服务端回归可使测试挂死；采纳最大页/条数及cursor前进检查。history已有重复task id断言，可提前拒部分重复页，仍需空页上限。
- 证据：frontend/tests/extensions.mjs:130

## OCR-1838 · 部分采纳

- 位置：frontend/tests/extensions.mjs:177；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 177 行：teardown 顺序与错误处理有问题。1) `hold?.()` 先放开挂起的模型响应，然后再 `stop()` 发 `:quit`，任务会在停机过程中继续执行工具/审批，可能产生额外 MCP 调用，污染 `peerWire` 计数类断言；2) `stop().catch(() => host?.kill())` 吞掉了 `stop()` 中 `assert.equal(host.exitCode, 0)` 的失败——非 0 退出会被静默掩盖成一次 kill；3) 若 `hold()` 在服务端已关闭后写响应会抛错并覆盖 finally 前的原始异常。建议先停机再释放挂起响应，且不要在 finally 中吞掉 break `stop()` 的断言失败（至少记录到 output）。
- 判断：finally hold后stop catch吞断言属实，诊断应记录并回收；hold释放发生检查结束后，不应称污染此前已执行计数断言。采纳清理错误保留/有界停止与释放安全处理。
- 证据：frontend/tests/extensions.mjs:177

## OCR-1839 · 部分采纳

- 位置：frontend/tests/extensions.mjs:78；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：第 78 行：`path.replace(root + '\\', '')` 只在 Windows 分隔符下生效。在 POSIX（Linux/macOS CI）上替换不会命中，results JSON 会写入绝对截图路径，泄露 checkout 位置，也使产物在不同机器上不可复现。建议改用 `import { relative } from 'node:path'` 后 `relative(root, path)`。
- 判断：frontend/tests/extensions.mjs截图相对路径使用root加反斜杠替换，POSIX无法剥离；采纳path.relative再统一分隔符保证可搬运证据。绝对checkout路径本身不是密钥泄漏，不采纳高风险表述。
- 证据：frontend/tests/extensions.mjs:78

## OCR-1840 · 不采纳

- 位置：frontend/tests/extensions.mjs:38；OCR原严重度：medium
- 计划：无
- 原意见：第 38 行（配合第 15 行的 `parentSent`/`childSent` 一次性全局标志）：只有 `wire.length === 1` 的第一个请求会被挂起，且 `parentSent` 一旦置位就不会复位。若宿主在 `requestTimeoutSeconds: 20` 内发生重试，或测试在 `until(() => !!hold)` 之后迟迟未推进审批，重试请求会立刻收到一个不带 tool_calls 的 stop 响应，任务以“正常结束”收场，后续所有 approvals/tool.completed 断言都以误导性的方式失败或超时。建议按请求序号/会话键维护状态，遇到未预期的重复父请求时显式返回错误并记录，而不是返回空 stop。
- 判断：fixture一次性parent/child状态刻意锁定一轮协议，SDK不自动重试；意外重复应让测试失败而非适配为成功。可补明确错误诊断，不为不存在的自动重试实现复杂状态机。
- 证据：frontend/tests/extensions.mjs:38

## OCR-1841 · 部分采纳

- 位置：frontend/tests/extensions.mjs:167；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：第 167 行：切换为断连 MCP 配置的 `api('/v1/settings', disconnected)` 返回值未做任何校验。若保存被拒（4xx/WORKSPACE_BUSY 等），后续任务仍按旧配置运行，第 169 行的 `until(phase === 'UNKNOWN')` 只会以 `Fixture timeout` 失败，掩盖真正的失败原因。建议断言 `status === 200` 失败时立即报错。
- 判断：断连配置保存响应未断status，后续UNKNOWN只超时，补保存成功/设置身份断言可准确定位前置失败。
- 证据：frontend/tests/extensions.mjs:167

## OCR-1842 · 采纳

- 位置：frontend/tests/extensions.mjs:171；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 171 行：用 `peerWire.filter(w => w.method === 'tools/call').length === 1` 来证明“UNKNOWN 未被重放”过弱——被重放的调用指向断连的 `http://127.0.0.1:1/mcp`，根本到达不了 peer，计数必然保持 1，因此该断言无论是否发生重放都会通过。建议改为检查该任务的事件流中不存在第二条/新增的 `mcp_notes__*` 调用尝试（如 tool.failed/tool.completed 的 payload 计数）或宿主记录的 invocation attempt，并结合 inventory `recorded === false` 一起断言。
- 判断：新断连URL在127.0.0.1:1不触原peerWire，旧peer调用数不增不能证明断连任务未重放；应比对该task事件/工具尝试事实与请求次数。
- 证据：frontend/tests/extensions.mjs:171

## OCR-1843 · 部分采纳

- 位置：frontend/tests/extensions.mjs:175；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 175 行：失败路径只判断 `if (page)`，未判断页面/上下文是否已关闭。若失败发生在浏览器崩溃、`browser.close()` 之后（或页面被导航销毁），`page.screenshot(...)` 或 `page.locator('body').innerText()` 会再次抛错，从而覆盖 catch 中的原始异常 `e`，使真正失败原因丢失。建议把诊断写入包在 try/catch 内，失败时忽略诊断异常并保留原始 `e`。
- 判断：catch仅if(page)截图，页面关闭/崩溃时诊断可能再抛遮原错误；采纳诊断尽力执行且原错误始终保留。
- 证据：frontend/tests/extensions.mjs:175

## OCR-1844 · 部分采纳

- 位置：frontend/tests/diagnosis.mjs:12；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：启动 Java host 的方式有三处缺陷：(1) JAVA_HOME 未设置时 process.env.JAVA_HOME 为 undefined，path.join 会在 try 之前抛 TypeError，既不写 M10.5.1-diagnosis-failure.txt 也不进入 finally，失败无任何诊断信息；(2) spawn 未传 env，子进程继承全部父进程环境变量（含令牌/凭据），与“环境变量白名单”要求冲突；(3) 未监听 'error' 事件，java 或 JAR 不存在时产生的异步 error 会变成未捕获异常直接崩溃，同样绕开失败产物。建议在 try 之前显式校验 JAVA_HOME、传入白名单 env，并注册 host.on('error')。
- 判断：采纳具体测试启动诊断缺口：frontend/tests/diagnosis.mjs的spawn/接收路径无启动error监听或按完整行解析保护，错误JDK/拆分端口可造成无结构异常。继承受信任测试宿主环境不是已证明凭据泄漏，不采纳该安全严重度。
- 证据：frontend/tests/diagnosis.mjs:12

## OCR-1845 · 部分采纳

- 位置：frontend/tests/diagnosis.mjs:98；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：清理链路不可靠：browser.close() 一旦 reject，后续 host.stdin.end() 与 host.kill() 全部被跳过，Java host 被泄漏；stdin.end() 与第 89 行重复调用，在子进程已退出、流已销毁时可能抛出 ERR_STREAM_DESTROYED/EPIPE 而中断清理；固定 wait(500) 后只发一次 SIGTERM，既不等待也不升级 SIGKILL，挂死的 host 会残留。建议分步 try/catch，先等待退出，再依次 SIGTERM/SIGKILL 升级。
- 判断：frontend/tests/diagnosis.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：frontend/tests/diagnosis.mjs:98

## OCR-1846 · 部分采纳

- 位置：frontend/tests/diagnosis.mjs:16；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：host 的 stdout/stderr 被无上限地累加进单个字符串（Buffer 隐式转字符串还会按块解码，存在多字节字符被截断的风险），requests 数组同样无上限收集并在结果 JSON 中整体序列化。长时间运行或请求量大时会造成内存与产物膨胀（无界数据处理）。建议保留尾部固定长度输出，或按需开启详细采集。
- 判断：frontend/tests/diagnosis.mjs确逐块累加输出/请求字符串，运行时间有限不等于字节有界；采纳尾部日志预算、截断标记及UTF-8连续解码，防止失败诊断本身耗尽内存。尚无当前fixture异常输出的实际OOM证据。
- 证据：frontend/tests/diagnosis.mjs:16

## OCR-1847 · 部分采纳

- 位置：frontend/tests/diagnosis.mjs:34；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：ended() 对全部场景都把 UNKNOWN 当成正常结束：shell、write（审批过期）等本应落到 FINISHED 的任务若因状态机回归变成 UNKNOWN，此处仍会通过，并继续基于未决事实做后续断言，从而掩盖状态迁移缺陷。UNKNOWN 只应在明确期望“结果待核实”的场景（timeout/cancel）被接受。建议让调用方显式声明期望阶段。
- 判断：ended把FINISHED/UNKNOWN/VERIFIED均视停止是合理等待，但正常shell/拒绝场景须额外断预期phase。采纳逐场景终态断言；不能将所有cancel/timeout强制成功结算。
- 证据：frontend/tests/diagnosis.mjs:34

## OCR-1848 · 不采纳

- 位置：frontend/tests/diagnosis.mjs:86；OCR原严重度：low
- 计划：无
- 原意见：这里把任务提交次数硬编码为 4，与当前恰好创建 4 个任务（shell/timeout/write/cancel）强耦合，任何新增或删减场景都会让该断言误报为“刷新导致重复提交”。应在 reload 之前记录基线计数并与基线比较，避免魔法数字。
- 判断：frontend/tests/diagnosis.mjs是固定步骤的受控fixture；精确次数用于锁定请求/审批契约，嵌套条件不产生已证实运行错误。未来新增步骤需同步断言不是当前缺陷，不能仅按OCR风格规则重写。
- 证据：frontend/tests/diagnosis.mjs:86

## OCR-1849 · 不采纳

- 位置：frontend/tests/diagnosis.mjs:22；OCR原严重度：high
- 计划：无
- 原意见：Bearer 凭据以字面量硬编码在源码中（m10_browser_fixture_...），一旦被替换为真实令牌就是提交进仓库的密钥；该字符串还会随异常栈/失败产物外泄。建议改为从仅测试使用的环境变量读取并在缺失时快速失败，避免凭据进入版本库。
- 判断：m10_browser_fixture_为公开合成fixture口令，非生产秘密；假设后来被替换成真实token不构成当前泄密，改依赖环境反增加误用真实凭据风险。
- 证据：frontend/tests/diagnosis.mjs:22

## OCR-1850 · 部分采纳

- 位置：frontend/tests/diagnosis.mjs:63；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：交接文件使用固定文件名，同样地截图（第 35 行 M10.5.1-${name}.png）、结果 JSON（第 93 行 M10.5.1-diagnosis-results.json）和失败产物（第 95-96 行）都没有带上本次 run 标识。并发或连续执行会互相覆盖证据；更糟的是失败运行时不会重写 results.json，目录里会残留上一次 PASS 的产物，导致证据与本次执行不一致。建议所有产物统一以 run 作为前缀/后缀。
- 判断：诊断截图/结果固定文件名，失败不覆盖PASS；矩阵目前顺序冻结降低并发但仍可能留旧报告。采纳唯一run目录/状态+摘要绑定及失败回执，不把陈旧证据当新一轮。
- 证据：frontend/tests/diagnosis.mjs:63

## OCR-1851 · 部分采纳

- 位置：frontend/tests/diagnosis.mjs:48；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：点击“执行诊断”后立即读取 innerText 断言 SHELL_REQUIRED，没有等待诊断事实拉取/渲染完成，服务响应稍慢就会读到上一个任务残留的面板内容而偶发失败（第 84 行断言“已请求取消”存在同样的竞态）；并且 innerText() 被重复调用两次。建议先用 locator.waitFor 等待目标文本出现，再取样一次文本做断言。
- 判断：shell已先wait命令主体未启动，不能称点击后全无等待；取消后检查只等ended，面板可能未更新。仅补取消/诊断目标文本显式等待和单次读取。
- 证据：frontend/tests/diagnosis.mjs:48

## OCR-1852 · 部分采纳

- 位置：frontend/tests/development.mjs:0；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：第 9 行：`JAVA_HOME` 未设置时 `path.join(undefined, ...)` 会抛 `ERR_INVALID_ARG_TYPE`。由于 `steps`（第 15-19 行）在模块加载阶段就调用 `exe('javac')`，缺变量会让脚本在 try/catch 之前崩溃，既不产出 results 也没有可读提示。建议在文件顶部显式校验 `JAVA_HOME` 与 `harness-cli/target/harness-cli-*.jar` 是否存在，并以明确消息失败（如 `throw new Error('JAVA_HOME is not set')`）。
- 判断：frontend/tests/development.mjs直接从JAVA_HOME构造JDK路径，确无缺项/文件存在前置提示；采纳清楚的fixture前置失败回执。CI已设置JDK，缺变量失败不是正常验收误通过，不采纳为高严重度产品缺陷。
- 证据：frontend/tests/development.mjs:9

## OCR-1853 · 不采纳

- 位置：frontend/tests/development.mjs:0；OCR原严重度：high
- 计划：无
- 原意见：第 30 行：spawn 用 `...process.env` 把父进程的全部环境变量透传给被测 harness，违反“环境变量白名单”要求。CI/开发机上无关的凭据（云厂商 AK/SK、GITHUB_TOKEN 等）以及会改变行为的变量（JAVA_TOOL_OPTIONS、_JAVA_OPTIONS、各类 JHARNESS_*）都会泄漏给 harness 及其派生的 javac/java，同时让用例结果依赖外部环境、不可复现。建议只透传白名单（PATH、PATHEXT、SystemRoot、HOME、TMPDIR/TEMP、JAVA_HOME、LANG/LC_ALL），再显式注入 JHARNESS_WEB_TOKEN 与 JHARNESS_API_KEY。
- 判断：测试宿主/SDK本身是受信任被测本机服务，继承运行环境不等同工具执行的白名单绕过；该文件显式覆写合成fixture凭据。未提供真实无关变量被记录或外发证据，不能把继承环境本身判为已发生泄漏。
- 证据：frontend/tests/development.mjs:30

## OCR-1854 · 部分采纳

- 位置：frontend/tests/development.mjs:0；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 28 行：`provider.listen` 没有注册 `'error'` 监听。端口绑定失败时 Node 会抛出未处理的 `error` 事件直接终止进程（此时尚未进入 try/finally，harness 子进程与浏览器无从清理），且该 Promise 永远不会 settle，失败只能靠外部超时暴露。建议 `provider.once('error', reject)`，listen 回调后再判空 `provider.address()`。
- 判断：provider.listen/初始化位try之外且无error拒绝通道，初始化中途失败可无统一清理/回执。采纳把全部拥有资源纳入生命周期和前置错误响应；不存在资源不要无条件close。
- 证据：frontend/tests/development.mjs:28

## OCR-1855 · 部分采纳

- 位置：frontend/tests/development.mjs:26；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 26 行：事件分页是无界循环——没有最大页数保护，也没有校验游标前进。若服务端返回 `more:true` 但 `next` 为空或与上次相同，这里会无限循环；`rows` 同样没有总量上限，违背有界数据处理要求。建议加入最大页数（如 50）、断言 `p.next !== after`，超限直接失败。
- 判断：各events/history helper循环只按游标/more继续，无全局截止/重复cursor集合，服务端回归可使测试挂死；采纳最大页/条数及cursor前进检查。history已有重复task id断言，可提前拒部分重复页，仍需空页上限。
- 证据：frontend/tests/development.mjs:26

## OCR-1856 · 部分采纳

- 位置：frontend/tests/development.mjs:0；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：第 51 行：清理路径既没有错误处理也没有等待退出。`host.stdin.end(':quit\n')` 在子进程已关闭 stdin（或已退出但 `exitCode` 尚未刷新）时会触发未处理的 `'error'` 事件（EPIPE），作为未捕获异常抛出，直接覆盖 catch 中已写入的原始断言错误与失败产物；`host.kill()` 后只发信号不等待 `'exit'`，可能留下孤儿 java 进程并让 Node 事件循环挂住。建议给 `host.stdin` 注册 `on('error', ...)`、把写操作包在 try/catch，`kill()` 后 `await once(host,'exit')`，超时再发 SIGKILL。
- 判断：frontend/tests/development.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：frontend/tests/development.mjs:55

## OCR-1857 · 部分采纳

- 位置：frontend/tests/development.mjs:0；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：第 24 行：脱敏标记只检查了模型侧 `secret`，没有检查 web bearer `token`（第 9 行常量）。若 harness 把 JHARNESS_WEB_TOKEN 泄漏到模型请求（错误地放进 prompt 或 header），第 48 行的 `wire.every(w=>w.authorized&&w.redacted)` 仍为真，脱敏断言形同虚设。建议统一遍历一组已知敏感值（secret、token，必要时连同 JAVA_HOME 等环境变量值）逐一检测并记录命中项。
- 判断：development wire.redacted只检测模型key不检测Web token，补两种已知秘密的模型上下文缺失断言合理；JAVA_HOME是路径而非必须脱敏秘密，不要求隐藏所有环境值。
- 证据：frontend/tests/development.mjs:24

## OCR-1858 · 部分采纳

- 位置：frontend/tests/development.mjs:0；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 50 行：脱敏只作用于写入 `M10.5.9-development-failure.txt` 的文本，随后 `throw e` 会把未脱敏的错误原样抛到控制台/CI 日志；`e.stack` 可能包含 `assert.ok(base, output)` 携带的 harness stdout（其中可能出现 `JHARNESS_API_KEY`/`JHARNESS_WEB_TOKEN`），凭据仍会外泄。建议对 `e.message`/`e.stack` 同样执行 replaceAll，或构造一个已脱敏的新 Error 再抛出。
- 判断：失败文件已替换key/token但throw原e可把含output的assert栈再写控制台，采纳同一脱敏错误摘要；当前值是合成秘密，不宣称真实key已外泄。
- 证据：frontend/tests/development.mjs:54

## OCR-1859 · 不采纳

- 位置：frontend/tests/development.mjs:42；OCR原严重度：low
- 计划：无
- 原意见：第 38 行（以及第 45 行的 `wire.length===12`）：`9` 与 `12` 是写死的魔法数字，与 `steps` 数组隐式耦合（11 个步骤 + 1 次收尾请求；3 次 edit + 6 次 command）。一旦增删步骤或 harness 调整审批粒度，用例会以“审批数/请求数不对”这类误导性信息失败。建议由 `steps` 推导期望值，例如 `steps.filter(([n])=>n==='edit_file'||n==='execute_command').length` 与 `steps.length+1`，并在断言消息中打印实际计数。
- 判断：frontend/tests/development.mjs是固定步骤的受控fixture；精确次数用于锁定请求/审批契约，嵌套条件不产生已证实运行错误。未来新增步骤需同步断言不是当前缺陷，不能仅按OCR风格规则重写。
- 证据：frontend/tests/development.mjs:42

## OCR-1860 · 部分采纳

- 位置：frontend/tests/development.mjs:0；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：第 24 行：假模型服务在 HTTP 回调里直接 `step++` 取下一步，既无串行化也无去重/校验。只要 harness 并发发起模型请求、超时重试一次，或先发一个探测/健康请求，脚本步骤就会整体错位：callId 由 `step` 生成，而断言又按键 `development-N` 回查，随后 `by(n)`/`wire.length===12`/`approvals===9` 会连带失败或误判；`wire.push` 也没有顺序保证，无法用于时序断言。建议用串行队列（Promise mutex）处理请求、校验请求体中最后一个工具结果与 `steps[step]` 期望一致（不一致直接返回 4xx 并记录），并把请求序号写入 wire；同时给 `for await(const c of req)` 包 try/catch，读体失败时不回响应会让 harness 一直挂到超时。
- 判断：固定steps[step++]无需mutex才能正确，Node同步增序号不会数据竞争；但应对未预期重复/非法请求记录明确协议失败，而非默认空stop。采纳请求步骤与历史关联校验/异常回执，不增加自动重试。
- 证据：frontend/tests/development.mjs:24

## OCR-1861 · 部分采纳

- 位置：frontend/tests/development.mjs:0；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：第 39 行：`!JSON.stringify(by(4).payload).includes('forbidden implicit shell\n')` 中 `JSON.stringify` 会把换行转义成 `\` + `n` 两个字符，因此带真实换行的查找串永远匹配不到，这个否定断言恒为真——即使隐式 shell 真的执行并输出了被禁止的内容，用例也会 PASS；另外 `by(4)` 缺失时会直接抛 TypeError 而非给出可读断言信息。建议改为结构化断言（`payload.status==='FAILED'` 且错误码为 SHELL_REQUIRED），若坚持做输出检测则使用转义后的串 `'forbidden implicit shell\\n'`，并先校验 `by(4)` 存在。
- 判断：JSON.stringify后的换行被转义，因此该否定子串确恒不命中；同check仍要求SHELL_REQUIRED不是整体恒真。采纳结构化FAILED/code/body-not-started断言和真实无输出检查。
- 证据：frontend/tests/development.mjs:43

## OCR-1862 · 部分采纳

- 位置：frontend/tests/development.mjs:41；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 37 行：断言名称声称“list/read 先于 edit”，实现却只检查这两步 `status==='SUCCESS'`，完全没有比较事件顺序。即使 harness 先执行 edit 再补 read、或 `--require-read App.java` 根本没生效，用例依旧通过（跨步骤必需操作排序未被真正验证）。建议在 `facts` 中定位首个 edit（`development-3`）的下标，断言 list/read 的 tool 事件下标严格更小；或另加一条负向步骤，验证乱序请求会被拒绝。
- 判断：当前steps固定依次发工具且前面审批前已验证源码未变，但该名称所称读写/错误所见的时间序没有直接断言。补事件序号与对应模型请求先后可证明声明，不能说整个真实构建流程无效。
- 证据：frontend/tests/development.mjs:41

## OCR-1863 · 部分采纳

- 位置：frontend/tests/development.mjs:48；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 44 行：`wire` 记录的是“该请求体是否含错误串”，但最后一次汇总请求会携带整段会话历史（含全部历史工具结果），因此只要错误最终出现过，这三个 `some` 必然为真——“在修复之前看到错误”的时序语义未被验证。建议在 wire 条目中记录请求序号，断言 `observedBuildError` 的首次出现序号早于修复步骤对应的请求（第 6/9 步），而不是做全局存在性判断。
- 判断：当前steps固定依次发工具且前面审批前已验证源码未变，但该名称所称读写/错误所见的时间序没有直接断言。补事件序号与对应模型请求先后可证明声明，不能说整个真实构建流程无效。
- 证据：frontend/tests/development.mjs:48

## OCR-1864 · 部分采纳

- 位置：frontend/tests/development.mjs:0；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 47 行：刷新后只比较 `wire.length`，只能证明“没有新增模型请求”，并没有验证页面确实从既有事实渲染出构建/测试结果，也没有验证事件是否被重复回放。前端若刷新后渲染空列表、或把本地缓存事件重复插入，此断言仍会通过。建议 reload 后断言页面出现 `TEST_PASS`/报告内容，并通过 DOM 计数或 `/v1/tasks/:id/events` 比对确认事件数与刷新前一致（无重复、无重放）。
- 判断：刷新只比较wire次数能证明不新增模型请求，不能证明已有构建结果实际重绘且无重复DOM；采纳TEST_PASS/事件关联与数量验证，保留原no-replay断言。
- 证据：frontend/tests/development.mjs:51

## OCR-1865 · 部分采纳

- 位置：frontend/tests/history.mjs:12；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：第 12 行在模块顶层直接使用 `process.env.JAVA_HOME`，未做存在性校验：未设置该变量时 `join(undefined, 'bin', ...)` 会在顶层抛 TypeError，此时第 27-31 行已创建目录、文件并启动了 mock provider，但还没进入 try/finally（53 行），进程会带错误崩溃并留下未关闭的 server 句柄（详见第 30 行评论）。Windows 下 JAVA_HOME 若带引号，拼接出的路径同样非法，只在 spawn 阶段表现为难以定位的 ENOENT。建议先校验并给出可读错误信息。
- 判断：frontend/tests/history.mjs直接从JAVA_HOME构造JDK路径，确无缺项/文件存在前置提示；采纳清楚的fixture前置失败回执。CI已设置JDK，缺变量失败不是正常验收误通过，不采纳为高严重度产品缺陷。
- 证据：frontend/tests/history.mjs:12

## OCR-1866 · 不采纳

- 位置：frontend/tests/history.mjs:34；OCR原严重度：high
- 计划：无
- 原意见：子进程 `env: { ...process.env, ... }` 全量继承父进程环境，与“环境变量白名单”要求冲突：运行机上真实的模型 API Key、代理凭据、云凭证都会进入被测宿主及其插件作用域，测试无法证明宿主只读取配置声明的 `apiKeyEnv`。同时第 124 行失败日志只对 `key`/`token` 两个值做 replaceAll 脱敏，任何经由继承环境进入 `hostOutput` 的其他凭据都会原样落盘到 `.build-cache/...failure.txt`。建议显式构造白名单 env（PATH、JAVA_HOME、LANG 及本用例两个变量）。
- 判断：测试宿主/SDK本身是受信任被测本机服务，继承运行环境不等同工具执行的白名单绕过；该文件显式覆写合成fixture凭据。未提供真实无关变量被记录或外发证据，不能把继承环境本身判为已发生泄漏。
- 证据：frontend/tests/history.mjs:34

## OCR-1867 · 部分采纳

- 位置：frontend/tests/history.mjs:35；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`spawn` 之后没有注册 `host.on('error')`。触发条件：java 不存在（JAVA_HOME 误配）或不可执行 → 抛出带 ENOENT 的 'error' 事件且无监听者，直接成为未捕获异常；即使被吞掉，`host.exitCode` 仍保持 null，第 36 行的 `until` 会空转 25s 后抛出通用的 "History fixture condition timed out"，掩盖真实原因。同时若 WEB 行日志格式变化导致匹配失败，也只有超时、没有 stdout 摘要。建议监听 'error' 立即失败并把 `hostOutput` 尾部带入错误消息。
- 判断：实际spawn未注册error事件，坏java/python可未捕获退出且无结构化fixture失败回执；采纳error/close监听与前置诊断。classpath缺失是Java非零退出非spawn error，原意见这部分不准确。
- 证据：frontend/tests/history.mjs:35

## OCR-1868 · 部分采纳

- 位置：frontend/tests/history.mjs:30；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：初始化副作用（mkdir/writeFile/listen，27-31 行）全部发生在 try 之外，只有 finally（126 行）负责关闭 provider。触发条件：第 31 行写配置文件失败、或第 30 行 listen 失败时，finally 不会被调用，provider 监听句柄仍打开，Node 事件循环不会退出，测试进程直接挂死而不是报错退出。另外 `listen` 没有 `error` 监听（如 EADDRINUSE），会在顶层 await 处抛出未处理的 'error' 事件崩溃。建议把 provider 创建与 listen 移入 try，或注册 process 级清理钩子。
- 判断：provider.listen/初始化位try之外且无error拒绝通道，初始化中途失败可无统一清理/回执。采纳把全部拥有资源纳入生命周期和前置错误响应；不存在资源不要无条件close。
- 证据：frontend/tests/history.mjs:30

## OCR-1869 · 部分采纳

- 位置：frontend/tests/history.mjs:21；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：mock provider 的请求处理器没有任何错误处理：请求体不是 JSON（健康检查/其它路径）或 `body.messages` 缺失时，`JSON.parse` / `.some` 会抛错，async 处理器返回 rejected Promise → 进程级 unhandledRejection，且不会写回响应，宿主只能等到 `requestTimeoutSeconds: 5` 超时，测试表现为“任务超时”而非协议错误，排查成本很高。建议 `try/catch` 包裹并对非法请求回 400，同时校验 `Array.isArray(body.messages)`。
- 判断：frontend/tests/history.mjs的loopback合成provider确在async handler直接解析JSON，异常缺统一失败响应/回执；采纳非法请求时快速、可诊断地失败及请求字节预算。此provider仅测试替身，不是产品公网协议漏洞。
- 证据：frontend/tests/history.mjs:21

## OCR-1870 · 部分采纳

- 位置：frontend/tests/history.mjs:100；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：游标分页循环没有迭代上限。触发条件：服务端返回的 `next` 不推进（同一游标、或空 `tasks` 且 `next` 指向自身）时，`while (next)` 永不退出，持续向被测服务无限发请求并把测试挂死，`until` 的超时保护在此处不生效。建议记录 `seenCursors` 或设定最大页数（如 `Math.ceil(first.historyCount / 24) + 2`）后断言终止。
- 判断：各events/history helper循环只按游标/more继续，无全局截止/重复cursor集合，服务端回归可使测试挂死；采纳最大页/条数及cursor前进检查。history已有重复task id断言，可提前拒部分重复页，仍需空页上限。
- 证据：frontend/tests/history.mjs:100

## OCR-1871 · 部分采纳

- 位置：frontend/tests/history.mjs:120；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：凭据泄漏检查实质为空：(1) `Storage` 的属性不可枚举，`JSON.stringify({ localStorage, sessionStorage })` 只会得到 `{"localStorage":{},"sessionStorage":{}}`，无论 token/key 是否被写进 storage 都检出不了，应显式遍历 `Object.entries(localStorage)`/`Object.keys` 再拼接值；(2) 只搜索了 `key`，没有搜索 web 端 bearer `token`；(3) `start()` 在第 33 行每次重置 `hostOutput`，重启前的宿主输出被丢弃，本检查只覆盖重启之后的日志，首次运行期间的泄漏不会被发现。
- 判断：Storage在浏览器可枚举，JSON.stringify不必为空，OCR首项错误；但这里只查key且start重置hostOutput，漏Web token/首次输出属实。补所有已知秘密与各启动分段，不因错误前提照抄Object.entries。
- 证据：frontend/tests/history.mjs:120

## OCR-1872 · 部分采纳

- 位置：frontend/tests/history.mjs:60；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：`find(...)` 结果未判空即取 `.id`。当注册接口返回的名称被规范化（例如 trim/重命名）或注册失败时，这里会抛 `Cannot read properties of undefined (reading 'id')`，掩盖真正的失败点（应为“未找到 Beta project 工作区，实际返回 …”）。建议断言 `beta` 存在。
- 判断：find返回值直接取字段，缺项会非零TypeError而不是假通过；采纳先断必需对象并带实际返回摘要，提升失败定位。
- 证据：frontend/tests/history.mjs:60

## OCR-1873 · 不采纳

- 位置：frontend/tests/history.mjs:40；OCR原严重度：high
- 计划：无
- 原意见：`api()` 始终附带合法 Bearer token，全流程没有任何一次“缺失/失效 token 应返回 401”的用例，因此“无 OAuth 的产品 API 鉴权”这一安全不变式在本用例中被完全跳过（即使服务端放行了未鉴权请求，测试也全绿）。另外 `await r.json()` 未判断状态码与 content-type：401/403 若返回 HTML 或空体，会抛 JSON 解析错误，掩盖真实 status，使 63 行这类状态断言难以定位。建议增加无 token/错 token 的 401 断言，并对非 JSON 响应保留原始文本。
- 判断：history不需要重复每项鉴权测试，browser.mjs已对无效token断401且Java服务器有鉴权专项；api helper JSON失败会fail-closed，无证据整体鉴权未测。
- 证据：frontend/tests/history.mjs:40

## OCR-1874 · 部分采纳

- 位置：frontend/tests/history.mjs:76；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：导出断言过弱：只校验每行 sessionId 一致 + 单个 sentinel 字符串存在 + 不含两个凭据，无法发现事件级丢失（例如只导出部分事件、丢失 tool 输出之外的事件、顺序错乱、分叉/置顶事件缺失）。因此“export is complete”名不副实——归档/分叉前的事件被裁掉仍能通过。建议断言事件条数与关键 type 序列。另外 74 行的 `download` 等待未设超时、固定文件名 `M10.5.2-session-export.jsonl` 在并发运行时互相覆盖。
- 判断：export/归档恢复目前仅sentinel、状态/事件存在性，名称宣称完整性超出断言；采纳原始事件完整序列/条数/关联及元数据保持。下载wait有Playwright默认超时，不称无期限。
- 证据：frontend/tests/history.mjs:76

## OCR-1875 · 部分采纳

- 位置：frontend/tests/history.mjs:83；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：归档/恢复只校验 `archived` 布尔值与单个事件存在：名称（Beta investigation）、置顶状态、`session.forked` 的父会话关联、事件总数都未重新核对，因此“archival preserves history / restoration retains all original facts”无法检出历史被清空或字段被重置；同时 check 名称声称 "switches archived view"，但没有任何 UI 归档视图的断言（仅调 API），UI 切视图失效也不会被发现。
- 判断：export/归档恢复目前仅sentinel、状态/事件存在性，名称宣称完整性超出断言；采纳原始事件完整序列/条数/关联及元数据保持。下载wait有Playwright默认超时，不称无期限。
- 证据：frontend/tests/history.mjs:83

## OCR-1876 · 不采纳

- 位置：frontend/tests/history.mjs:102；OCR原严重度：high
- 计划：无
- 原意见：`ids.at(-1).slice(-64, -56)` 依赖“任务 id 长度 ≥ 64 且 UI 展示的正是倒数第 64~56 位”这一隐含约定。若 id 采用其它格式（短 id、带前缀、长度 < 64），slice 返回空字符串，`includes('')` 恒为真，本行等待条件立刻满足；随后第 103-105 行“翻页后内容不同”的判定同样失效，整个分页用例变成空断言并通过。建议改用完整的 id 尾部固定长度，或直接断言列表项包含该任务的 goal 文本。
- 判断：当前生产taskId固定web-32hex-64hex，App实际显示相应截段；更换短id是未来协议变更，空slice假设不是当前路径。可改goal定位增强可读性，非必须修复。
- 证据：frontend/tests/history.mjs:102

## OCR-1877 · 不采纳

- 位置：frontend/tests/history.mjs:104；OCR原严重度：medium
- 计划：无
- 原意见：在 `evaluate` 里同步连点两次“下一页会话”，既绕过 Playwright 的可操作性/防双击检测，又不等待第一次点击后的重渲染：若两次点击都被消费，页面直接跳到第 3 页，第 104 行对 `'2'` 的断言随机失败；若前端做了防抖把双击并成一次，则“重复点击只翻一页”的语义根本没被验证。结果取决于时序，属于不稳定用例。建议显式分为两步并等待页码变化，或断言连点后页码仍为 2。
- 判断：同一evaluate双击刻意测试pageMoving同步锁，随后已明确断页码==='2'与内容变化；建议再次断页码是现有内容，拆两步反而失去双击覆盖。
- 证据：frontend/tests/history.mjs:104

## OCR-1878 · 采纳

- 位置：frontend/tests/history.mjs:112；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：Home/Tab 按键的结果没有任何断言，唯一的断言是 `tagName === 'SELECT'`，与键盘焦点行为无关——即使 Home 跳走了焦点、Tab 把焦点留在不可达元素上、picker 完全无法用键盘操作，本检查依旧通过。建议断言 `document.activeElement` 在按 Home 后仍为该 select、Tab 后焦点离开且顺序合理。
- 判断：focus/Home/Tab后仅tagName===SELECT，与焦点行为无关；应断activeElement和离开顺序，原生控件语义不能替代实际键盘结果。
- 证据：frontend/tests/history.mjs:112

## OCR-1879 · 部分采纳

- 位置：frontend/tests/history.mjs:89；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：工作区过滤断言只覆盖 `/v1/tasks` 的默认第一页（未分页），且 `every` 对空数组恒为 true：当过滤实现返回空列表（最典型的越权/过滤 bug 表现）时该断言仍然通过；跨页的其它 profile 任务也不会被检查。建议对 `view=all` 全量分页后断言，并显式断言 `tasks.length > 0`。
- 判断：过滤every空集恒真属实，前序已创建alpha但未直接要求列表非空；补新建task确在集合和全分页profile一致性。
- 证据：frontend/tests/history.mjs:89

## OCR-1880 · 部分采纳

- 位置：frontend/tests/content.mjs:11；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：这里没有对 `process.env.JAVA_HOME` 做空值校验：环境变量未设置时 `join(undefined,'bin/java')` 会在模块顶层（try 之外）直接抛 TypeError，脚本连 finally 清理都进不去，也不会写出结果 JSON；若 JAVA_HOME 指向错误目录，`spawn` 会同步抛 ENOENT，同样只得到无上下文的堆栈。jar 路径也没有存在性检查。建议显式校验 JAVA_HOME（缺失时回退到 PATH 中的 `java`），并在启动前用 `existsSync(jar)` 给出明确错误。
- 判断：frontend/tests/content.mjs直接从JAVA_HOME构造JDK路径，确无缺项/文件存在前置提示；采纳清楚的fixture前置失败回执。CI已设置JDK，缺变量失败不是正常验收误通过，不采纳为高严重度产品缺陷。
- 证据：frontend/tests/content.mjs:11

## OCR-1881 · 部分采纳

- 位置：frontend/tests/content.mjs:23；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：合成 provider 的 async 请求处理器没有任何异常保护：只要收到非 JSON 请求体、`body.messages` 不是数组或缺少 user 消息以外的结构异常，`JSON.parse` / `messages.findLastIndex` 就会抛出，导致未处理的 Promise rejection（Node 默认会终止进程），并且此时 `res.writeHead` 还没执行、也没有任何响应写出，客户端 fetch 会挂到 requestTimeoutSeconds=80s，而 `until()` 30s 就抛「Content fixture timeout」，真实原因被掩盖。另有分支判断缺陷：当 `last===-1`（无 user 消息）时 `goal=''`，`messages.slice(last+1)` 变成 `slice(0)` 扫描全部消息，`done` 可能被误判为 true 而走错分支；`content` 为数组（多模态）时所有 `goal===` 比较也会失效。建议 try/catch 后返回 400 并结束响应，同时显式校验 `last>=0` 与 content 为字符串。
- 判断：frontend/tests/content.mjs的loopback合成provider确在async handler直接解析JSON，异常缺统一失败响应/回执；采纳非法请求时快速、可诊断地失败及请求字节预算。此provider仅测试替身，不是产品公网协议漏洞。
- 证据：frontend/tests/content.mjs:23

## OCR-1882 · 部分采纳

- 位置：frontend/tests/content.mjs:171；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：finally 里的清理步骤是串行裸 await，前一步失败会中断后面所有清理：`browser.close()` 或 `stop()` 抛错时 `provider.closeAllConnections()/close()` 不会执行，监听中的 HTTP server 会让事件循环无法结束，脚本从「失败」变成「挂死」；而 `stop()` 本身就可能在两种情况下抛错——退出码非 0（`assert.equal(host.exitCode,0)`）和进程被信号杀死（此时 `exitCode` 保持 null，`until()` 30s 后超时）。同时 `hostStopped:host.exitCode===0` 只被写进结果、从未参与判定：若 host 在最后一次 start 之后被信号杀死，`stop()` 的守卫条件不成立而直接跳过断言，结果文件依旧是 `status:'PASS'` + `hostStopped:false`。另外任一断言失败都会跳过 finally 之后的 `writeFile`，失败运行不会留下 checks/errors 回执。建议每步独立 try/catch 并全部执行，把 `hostStopped` 纳入 PASS 判定，并把结果写入移到 finally 内。
- 判断：frontend/tests/content.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：frontend/tests/content.mjs:171

## OCR-1883 · 部分采纳

- 位置：frontend/tests/content.mjs:77；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：脱敏断言只检查 10 字符前缀，覆盖不到真正的风险面：provider 的第二个 chunk 已经把 `key.slice(10)`（剩余 36 字符）和 `otherKey.slice(10)` 写进了临时流，若服务端只裁掉了 `key.slice(0,10)` 而把剩余部分渲染出来，这两条断言仍会通过；秘密按其他对齐方式切分（例如起始下标错一位）同样检测不到。建议断言临时显示文本中不含任一长度为 N(>=8) 的秘密子串（或对完整 key/otherKey 及其各后缀做 includes 检查），而不是只比对固定前缀。
- 判断：流测试只查固定10字前缀，后chunk泄漏后缀可漏；补各受控分片/足够长后缀实际不可见断言，不要求任意短字符子串以免误报普通文字。
- 证据：frontend/tests/content.mjs:77

## OCR-1884 · 部分采纳

- 位置：frontend/tests/content.mjs:167；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：最后的秘密泄漏检查覆盖面过窄且时序不对：(1) 只取 `document.body.innerText` 与 Storage 的 JSON，DOM 属性（input value、aria-label、title、data-*）、cookie、IndexedDB、剪贴板都不在检查范围内，属性里回显的秘密会漏过；(2) 该断言在 host 重启、页面已切到 broken/UNAVAILABLE 视图之后才执行，此时含 `Redacted provider echo` 的 render 消息早已不在 DOM 中，即使渲染期泄漏过也不会被发现。建议在 render 消息可见时立刻对 `page.content()`（含属性）+ cookie + clipboard + IndexedDB 做一次完整断言，并在最终阶段再复查持久化存储。
- 判断：最终DOM检查发生broken重启后，无法证明此前render时安全；在受测消息可见时检查属性/input.value及storage合理。产品未用的IndexedDB/剪贴板路径不必泛化全检。
- 证据：frontend/tests/content.mjs:167

## OCR-1885 · 部分采纳

- 位置：frontend/tests/content.mjs:60；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：`ended()` 把 UNKNOWN 当成所有任务都合法的终态，并且只要出现「已同步全部记录 / 结果待核实 · 不会重放任务」其中之一就返回。这样任何任务（render/write/diff/long/cancel）一旦从正常路径退化成 UNKNOWN（例如持久化写失败、主机异常降级），后续断言仍会继续执行并通过，UNKNOWN 不得被重放的契约实际上没有被校验：cancel 任务就算返回 UNKNOWN 而不是 CANCELLED 也能过 `'cancelled stream does not become a complete answer'`。建议把期望终态参数化，默认只接受 FINISHED，只有明确允许 UNKNOWN 的用例（broken/重启恢复）才放开，并对 cancel 断言 CANCELLED。
- 判断：ended宽终态等待后应按场景检查phase/status，render/write若UNKNOWN也不能视成功；cancel允许保持未决，不能强制CANCELLED从而弱化UNKNOWN事实。
- 证据：frontend/tests/content.mjs:60

## OCR-1886 · 不采纳

- 位置：frontend/tests/content.mjs:44；OCR原严重度：low
- 计划：无
- 原意见：每次运行都在 `.build-cache/M10.5.6-content-<ts>/` 下新建工作区且从不清理，其中 `big.txt`、`.env.local`、`src/demo.ts` 都以明文写入了本轮生成的 key/otherKey，`.jharness/artifacts` 也会留下产物；多跑几次后磁盘上会堆积带秘密材料的目录，取消/中断的运行同样残留。建议在 finally 中用 `rm(run,{recursive:true,force:true})` 清理运行目录（截图与回执在 `out` 下，不受影响），或把工作区放到系统临时目录。
- 判断：key/otherKey为randomUUID前缀生成合成测试值，build-cache已gitignore且故意留证据可诊断；不是宿主真实凭据泄漏。磁盘留存策略可独立清理，不删除验收事实以假清秘密。
- 证据：frontend/tests/content.mjs:44

## OCR-1887 · 部分采纳

- 位置：frontend/tests/content.mjs:166；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：这条断言的名称与覆盖范围不符：`wire` 只在合成 provider 收到模型请求时 push，记录的是模型侧请求，完全不包含浏览器→应用、静态资源、健康检查等 HTTP 流量，也不记录目标 URL，因此既不能证明「all traffic」，也不能证明请求确实发往 loopback（只证明了带了 Bearer 且 stream=true）。例如应用把会话内容发到外部地址时这条断言依旧通过。建议改名成「all model requests ...」，或在 wire 中记录 url 并断言等于 provider 的 loopback origin，同时用 `requests`（page 层）+ `context.request` 拦截断言所有应用流量都指向 `base`。
- 判断：wire只含模型请求，check名all traffic过广；已有page requests.every(base)在早期HTML检查。采纳准确命名并集中所有请求origin断言，不凭名字推断已经外发。
- 证据：frontend/tests/content.mjs:166

## OCR-1888 · 部分采纳

- 位置：frontend/tests/content.mjs:115；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`artifacts.length>0` 对任何产物都成立，并没有验证「scoped artifact reference」：既没有断言产物属于当前任务（taskId/scope），也没有断言选中的 `artifacts[0]` 就是 big.txt 那次 read_file 的结果（可能拿到别的工具调用的产物），后续下载哈希校验因此可能在错误的产物上通过；`artifacts` 为 undefined 时还会在 `.length` 上抛 TypeError。建议先过滤（tool 名/源路径/大小）再断言恰好一条，并校验返回的 artifact 元数据（taskId、引用 id、页数、原始大小）与 events 中的 tool 结果一致。
- 判断：artifacts.length>0局部断言不证big.txt来源，但后续File record内容和下载摘要、跨task404已有覆盖；采纳把artifactRef与实际read_file event绑定，别重复造相同覆盖。
- 证据：frontend/tests/content.mjs:115

## OCR-1889 · 部分采纳

- 位置：frontend/tests/content.mjs:126；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这条断言只读取磁盘上的 changed.txt 内容，无法验证「change card 基于真实工具版本」：上面 `waitFor()` 的 `.changed-file` 只是等待元素出现，卡片展示的 before/after 版本、sha、工具调用 id 全都没有比对。即使卡片渲染的是伪造/过期版本快照，这里也会通过。建议读取 `.changed-file` 的可见文本/属性（路径、版本哈希、工具调用 id），并与 `/v1/tasks/<id>/events` 中工具结果记录交叉校验。
- 判断：changed-file等待只验路径，磁盘最终内容不证before/after版本显示；补卡片hash/callId与工具事件交叉核对。
- 证据：frontend/tests/content.mjs:126

## OCR-1890 · 部分采纳

- 位置：frontend/tests/content.mjs:108；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：越权预览用例的名字用了 `path.split('/').at(-1)`，`../outside/private.txt`、`outside+'/private.txt'`、`escape-link/private.txt` 三个用例都产出同一个检查名 'denied preview private.txt'，结果回执里出现重复条目，失败时无法区分是哪条路径破了边界；另外这里只断言 status===400，不校验错误码/原因，服务端因为完全不相干的原因统一返回 400（或把越界路径当作受保护元数据拒绝）也会通过。建议在名字里带上用例序号或类别，并断言具体 error code。
- 判断：三个private.txt检查重名确弱诊断，只断400不能识别真实拒绝原因；采纳唯一case类别和错误码，保留根/链接/秘密路径各反例。
- 证据：frontend/tests/content.mjs:108

## OCR-1891 · 部分采纳

- 位置：frontend/tests/models.mjs:11；OCR原严重度：high
- 计划：R0-03 / P3
- 原意见：JAVA_HOME 未做任何校验：未设置时 `join(undefined, ...)` 会抛 `TypeError: The path argument must be of type string`，报错信息与真实原因无关；JAVA_HOME 指向错误 JDK 时 `spawn` 失败只能等 30s 的 `until` 超时。建议启动前显式校验并给出可读错误。
- 判断：frontend/tests/models.mjs直接从JAVA_HOME构造JDK路径，确无缺项/文件存在前置提示；采纳清楚的fixture前置失败回执。CI已设置JDK，缺变量失败不是正常验收误通过，不采纳为高严重度产品缺陷。
- 证据：frontend/tests/models.mjs:11

## OCR-1892 · 不采纳

- 位置：frontend/tests/models.mjs:0；OCR原严重度：high
- 计划：无
- 原意见：`{...process.env}` 把无关的凭据、代理与调试变量全部透传给被测进程：测试不再 hermetic，而且正好绕过了本项目“环境变量白名单”这一被审查的行为，无法通过本测试验证子进程只拿到必要变量；同时 host 的 stdout/stderr 被累加进 `output`，一旦透传的秘密出现在诊断输出中就会被带出。建议改成显式白名单（PATH/JAVA_HOME/HOME/TMPDIR(+Windows SYSTEMROOT) + JHARNESS_*）。
- 判断：测试宿主/SDK本身是受信任被测本机服务，继承运行环境不等同工具执行的白名单绕过；该文件显式覆写合成fixture凭据。未提供真实无关变量被记录或外发证据，不能把继承环境本身判为已发生泄漏。
- 证据：frontend/tests/models.mjs:64

## OCR-1893 · 部分采纳

- 位置：frontend/tests/models.mjs:43；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：请求体读取没有大小上限，也没有解析失败处理：`raw` 会随请求无限增长；`JSON.parse(raw)` 抛错后 async handler 变成未处理的 Promise rejection（Node 默认直接终止进程），或者连接一直挂到 `requestTimeoutSeconds=80` 才超时，真正原因被“Model fixture timeout”掩盖。建议限制字节数并在解析失败时返回 400 并结束响应。
- 判断：frontend/tests/models.mjs的loopback合成provider确在async handler直接解析JSON，异常缺统一失败响应/回执；采纳非法请求时快速、可诊断地失败及请求字节预算。此provider仅测试替身，不是产品公网协议漏洞。
- 证据：frontend/tests/models.mjs:43

## OCR-1894 · 部分采纳

- 位置：frontend/tests/models.mjs:65；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：`stop()` 没有任何 kill/超时兜底：host 忽略 `:quit` 或 stdin 已关闭（`host.stdin.end()` 的 EPIPE/EINVAL 也没有错误监听）时，只会等到 `until` 抛 'Model fixture timeout'，Java 进程与端口泄漏；而该断言在 finally 中抛出还会跳过后面的 `provider.close`。另外 `stoppedOrigins.add(base)` 在 `base` 为 undefined（启动失败）时会把 undefined 塞进集合，污染后续 `stoppedHostDiagnostic` 判断。建议：等待超时后 `host.kill('SIGKILL')`，并忽略 stdin 错误。
- 判断：frontend/tests/models.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：frontend/tests/models.mjs:65

## OCR-1895 · 部分采纳

- 位置：frontend/tests/models.mjs:105；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：清理步骤串行且没有相互隔离：`browser.close()` 或 `stop()` 任一抛错都会跳过 `provider.close()`；更关键的是 `provider.close()` 只停止接受新连接、不销毁已建立连接——如果断言在 `hold()` 之前失败，那条被 `hold` 挂起的请求永远不会写回，socket 保持打开，`close` 回调永不触发，进程直接挂死。建议改用 `try/finally` 逐项兜底，并在失败路径先调用 `provider.closeAllConnections()`（Node 18.2+）。
- 判断：frontend/tests/models.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：frontend/tests/models.mjs:105

## OCR-1896 · 部分采纳

- 位置：frontend/tests/models.mjs:92；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：“只用请求头鉴权”的断言不完整：只比较了头部是否正确，虽然记录了 `w.path` 却没有检查 key 是否出现在 URL（`?api_key=` / `?token=` 这类查询串拼 key 是常见实现），也没有排除路径中出现其它形式的凭据，因此凭据经 URL 泄漏时该断言仍会通过，而 URL 往往会被访问日志留存。建议补充 `!w.path.includes(key)` 并对查询参名做白名单校验。
- 判断：header+body检查未要求wire.path不含key，补URL/userinfo/query无已知secret断言可守只头鉴权；未证实当前codec真的URL携key。
- 证据：frontend/tests/models.mjs:92

## OCR-1897 · 部分采纳

- 位置：frontend/tests/models.mjs:96；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：`find()` 返回 undefined 时会抛 TypeError（例如模型被拒保存前后列表结构变化/被删除），错误信息无法定位问题。建议使用 `?.protocol==='responses'` 做空值保护，让断言以可读方式失败。
- 判断：find返回值直接取字段，缺项会非零TypeError而不是假通过；采纳先断必需对象并带实际返回摘要，提升失败定位。
- 证据：frontend/tests/models.mjs:96

## OCR-1898 · 部分采纳

- 位置：frontend/tests/models.mjs:47；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：Guard Anthropic content with Array.isArray before .some; also assert system/tools mapping instead of only tool_result.
- 判断：Anthropic some直接对content操作会让字符串消息TypeError，正常请求含文本与工具混合；采纳Array.isArray及system/tools映射的独立断言，现失败仍非误PASS。
- 证据：frontend/tests/models.mjs:47

## OCR-1899 · 不采纳

- 位置：frontend/tests/models.mjs:44；OCR原严重度：medium
- 计划：无
- 原意见：嵌套三元表达式（协议判定处，以及下方第 47 行 `done` 的判定同样如此），违反项目“禁止嵌套三元”的约定，且协议映射逻辑重复，容易在新增协议时漏改。建议抽成 `const protocolByPath = {'/responses':'responses','/messages':'anthropic'}` 之类的映射或 switch。
- 判断：frontend/tests/models.mjs是固定步骤的受控fixture；精确次数用于锁定请求/审批契约，嵌套条件不产生已证实运行错误。未来新增步骤需同步断言不是当前缺陷，不能仅按OCR风格规则重写。
- 证据：frontend/tests/models.mjs:44

## OCR-1900 · 不采纳

- 位置：frontend/tests/models.mjs:68；OCR原严重度：medium
- 计划：无
- 原意见：`settled()` 用默认 30s 超时，小于配置的 `requestTimeoutSeconds=80` / `durationSeconds=90`：慢 CI 上轮次可能合法地还没结束，测试就以 'Model fixture timeout' 误报失败；并且任务进入 FAILED 时不会提前失败，仍空转到超时，丢失真实错误。建议显式传入与 requestTimeout 匹配的超时，并在阶段为 FAILED/ERROR 时立即抛错。另：第 29-30 行对 `/v1/tasks/{id}` 重复请求了两次。
- 判断：settled等待固定快loopback响应故意短于产品最大90秒，夹具故障应快速失败，不需延长至产品全预算；task phase不含FAILED/ERROR，建议按不存在phase早退错误。可错误消息带事实但不采纳为功能缺陷。
- 证据：frontend/tests/models.mjs:68

## OCR-1901 · 采纳

- 位置：frontend/tests/models.mjs:103；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这里复用了第 66 行抓取的 `history`，它是第三轮（anthropic）与第四轮（anthropic SSE）之前的事件快照，后续轮次以及重启前产生的事件都没有被检查，所以“会话事件不含 API key”这一结论覆盖不全，后续轮次真实存在的密钥泄漏会被放过。建议此处重新 `await events(id)` 获取最新事件再断言。
- 判断：history在第三/四轮前取样，最终secret检查仍用旧history，确漏后续协议轮次。最终重新分页全部events并检查。
- 证据：frontend/tests/models.mjs:103

## OCR-1902 · 部分采纳

- 位置：frontend/tests/models.mjs:106；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：结果文件只在 try 正常结束时才写：任意断言失败都会先经 finally 抛出，`checks/screenshots/transportDiagnostics` 完全不落盘，排障只能翻 stdout。此外若 `start()` 在 spawn 之前抛错，`host` 为 undefined，这里的 `host.exitCode` 会抛 TypeError 覆盖原始错误。建议在 catch/finally 中写入失败状态的结果文件，并用 `host?.exitCode`。
- 判断：models失败时最终result JSON写入被跳过，旧PASS可残留；采纳失败状态/部分checks和诊断回执，host可选保护。不能把强行kill也记录成功退出。
- 证据：frontend/tests/models.mjs:106

## OCR-1903 · 部分采纳

- 位置：frontend/tests/runtime.mjs:14；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：stoppedHostDiagnostic is too permissive: gate it to webkit only, require known read-only method/routes, parse query with URLSearchParams, derive accepted hosts from origins (localhost/IPv6), and rethrow contract errors instead of swallowing all exceptions.
- 判断：stoppedHostDiagnostic已有精确停机origin和读路由约束，不是吞所有异常；仍应限WebKit已知消息、解析query参数并结合请求method证明只读。localhost/IPv6当前host固定127.0.0.1，不无由扩大接受面。
- 证据：frontend/tests/runtime.mjs:14

## OCR-1904 · 不采纳

- 位置：frontend/tests/runtime.mjs:6；OCR原严重度：medium
- 计划：无
- 原意见：`launch()` 不是 `async`，但在 channel 校验失败时会同步抛出异常，因此它并不总是返回 Promise。调用方写成 `browserType.launch().catch(...)`、放进 `Promise.all` 或包在 `try { await ... }` 之外时，该错误会逃逸成未捕获异常（甚至直接中断测试进程），与正常的启动失败行为不一致。建议改为 `async launch(...)`，让所有失败都以 rejected promise 形式呈现。
- 判断：当前所有launch调用都在try内await，错误channel同步抛同样被捕获；假设未来.catch调用者不属于现有路径。async包装可统一API但不是当前挂死缺陷。
- 证据：frontend/tests/runtime.mjs:6

## OCR-1905 · 部分采纳

- 位置：frontend/tests/runtime.mjs:9；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：展开顺序让环境变量 `JHARNESS_FIREFOX_EXECUTABLE` 无条件覆盖调用方显式传入的 `options.executablePath`，且没有任何提示：同一个 harness 在不同入口（run-m1059-platform.py 设置该变量、run-workbench-matrix.mjs 不设置）会静默启动不同的浏览器二进制，导致结果不可复现。建议显式定义优先级（调用方参数优先，或冲突时报错），并校验该路径存在，以便环境变量配错时给出可定位的错误。
- 判断：env executable有意指定私有Firefox副本覆盖默认，但若调用者同时提供不同executablePath应显式冲突拒绝/记录优先级；采纳身份和路径存在性记录，不改变prepare分支验收口径。
- 证据：frontend/tests/runtime.mjs:9

## OCR-1906 · 部分采纳

- 位置：frontend/tests/settings.mjs:13；OCR原严重度：high
- 计划：R0-03 / P3
- 原意见：JAVA_HOME 未设置时 `join(undefined, 'bin/java')` 会直接抛 `TypeError: The "path" argument must be of type string`，脚本在模块加载阶段就崩溃，且没有任何可读的前置条件提示。建议显式校验：缺失时回退到 PATH 中的 java，或抛出明确的“请设置 JAVA_HOME”错误。
- 判断：frontend/tests/settings.mjs直接从JAVA_HOME构造JDK路径，确无缺项/文件存在前置提示；采纳清楚的fixture前置失败回执。CI已设置JDK，缺变量失败不是正常验收误通过，不采纳为高严重度产品缺陷。
- 证据：frontend/tests/settings.mjs:13

## OCR-1907 · 部分采纳

- 位置：frontend/tests/settings.mjs:12；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：jar 路径硬编码了 `0.1.0-SNAPSHOT` 版本号且没有存在性校验：构件未构建时 spawn 会以 ENOENT 失败（又因缺少 'error' 监听变成未捕获异常），构件版本升级后会静默加载陈旧 jar，或直接失败在无关断言上。建议从构建元数据/环境变量解析路径，并在启动前用 `access()` 校验并报出清晰的前置条件错误。
- 判断：jar当前版本固定与pom一致，不能说将来升级已用陈旧包；缺JAR时java进程非零而非spawn ENOENT。仅采纳前置存在性/摘要身份错误诊断，不用glob模糊选包。
- 证据：frontend/tests/settings.mjs:12

## OCR-1908 · 部分采纳

- 位置：frontend/tests/settings.mjs:40；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：两个问题：(1) 子进程没有 'error' 监听，java/jar 不存在时 spawn 的 'error' 事件无人处理，会以未捕获异常直接终止进程；(2) `env: { ...process.env, ... }` 把宿主机全部环境变量透传给被测进程，未做白名单，运行者机器上真实的 API Key/凭据会被暴露给宿主，可能污染“缺失密钥”“事件不泄露密钥”等断言并能被宿主日志打印。建议监听 'error' 并只透传必要变量（PATH、JAVA_HOME、LANG、系统代理等）。
- 判断：采纳具体测试启动诊断缺口：frontend/tests/settings.mjs的spawn/接收路径无启动error监听或按完整行解析保护，错误JDK/拆分端口可造成无结构异常。继承受信任测试宿主环境不是已证明凭据泄漏，不采纳该安全严重度。
- 证据：frontend/tests/settings.mjs:40

## OCR-1909 · 部分采纳

- 位置：frontend/tests/settings.mjs:22；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：请求体解析没有 try/catch，也没有 `request.on('error')` / server `clientError` 处理。空 body、被中断的连接或任何非 JSON 请求（例如探活请求）都会让 async handler reject → unhandledRejection，同时该请求永不收到响应，页面侧会一直卡到超时，最终报错与真实断言完全无关。建议 try/catch 后返回 400，并挂接 request/server 的 error 事件。
- 判断：frontend/tests/settings.mjs的loopback合成provider确在async handler直接解析JSON，异常缺统一失败响应/回执；采纳非法请求时快速、可诊断地失败及请求字节预算。此provider仅测试替身，不是产品公网协议漏洞。
- 证据：frontend/tests/settings.mjs:22

## OCR-1910 · 部分采纳

- 位置：frontend/tests/settings.mjs:46；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：关闭流程缺少终止升级与错误处理：宿主忽略 ':quit' 时 `until` 超时抛错，而 finally 中的 `await stop().catch(() => { host?.kill() })` 只发 SIGTERM、不等待退出、也不重抛，真正原因被吞掉（可能留下僵尸 JVM 占用端口/凭据）；另外进程已退出时 `host.stdin.end()` 可能触发 EPIPE 'error'，stdin 无监听器会导致未捕获异常；`assert.equal(host.exitCode, 0)` 在信号终止时 exitCode 为 null，报错信息也不含退出信号原因。建议在 stop 内部实现 SIGTERM → 超时 → SIGKILL 并等待退出，给 stdin 挂 error 处理，finally 中至少打印关闭失败原因而非静默忽略。
- 判断：frontend/tests/settings.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：frontend/tests/settings.mjs:46

## OCR-1911 · 部分采纳

- 位置：frontend/tests/settings.mjs:155；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：清理子进程存在三个资源生命周期/诊断问题：(1) 只监听 'exit' 且无 'error' 监听与超时，java 缺失或进程卡住时该 Promise 永不 settle，整个用例挂死；(2) `cleanupOutput` 收集后从未被读取（死变量），清理失败时 assert 只输出固定文案，真实错误全部丢失；(3) 该 assert 位于 finally，失败会覆盖 try 中的原始异常，并使文件末尾的结果 JSON 写入永远不执行，CI 上丢失全部诊断信息（此时 `result` 可能为 undefined）。建议 'error' + 'exit' 双监听并加超时，失败时把 cleanupOutput 打进消息，finally 内只记录不抛出以保留原始异常。
- 判断：清凭据cleanup只等exit、无error/超时，cleanupOutput未入错误，finally断言可遮原失败。采纳有界清理/日志脱敏及双失败保留，不能吞清理失败仍写cleaned=true。
- 证据：frontend/tests/settings.mjs:155

## OCR-1912 · 部分采纳

- 位置：frontend/tests/settings.mjs:53；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：用硬编码的 Windows 分隔符 `'\\'` 剥离 root 前缀：在 POSIX 上 `root + '\\'` 不匹配，截图路径会以绝对路径写进结果 JSON，泄露本机目录结构且跨平台产物不可比较。建议直接用 node:path 的 `relative(root, path)`。
- 判断：frontend/tests/settings.mjs截图相对路径使用root加反斜杠替换，POSIX无法剥离；采纳path.relative再统一分隔符保证可搬运证据。绝对checkout路径本身不是密钥泄漏，不采纳高风险表述。
- 证据：frontend/tests/settings.mjs:53

## OCR-1913 · 部分采纳

- 位置：frontend/tests/settings.mjs:80；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：`find` 的结果未做空值校验，紧接着解引用 `selected.model` / `selected.keyConfigured`，连接未成功保存时会抛晦涩的 TypeError 而不是可读的断言失败。建议先 `assert.ok(selected, 'selected connection must exist')` 再解引用。
- 判断：find返回值直接取字段，缺项会非零TypeError而不是假通过；采纳先断必需对象并带实际返回摘要，提升失败定位。
- 证据：frontend/tests/settings.mjs:80

## OCR-1914 · 不采纳

- 位置：frontend/tests/settings.mjs:31；OCR原严重度：medium
- 计划：无
- 原意见：`held` 是单槽位：在 block 为 true 期间若出现第二个请求（host 重试、并发连接、keep-alive 复用后的新请求），会覆盖 held，前一个请求永远收不到响应，整个用例死等到超时；同时 `wire.length` 变成 2，`until(() => wire.length === 1)` 从此永远不成立，错误信息会被误导成“等待 fixture 条件超时”。建议用 Map 按请求标识保存 pending reply，或在 block 期间对每个请求排队并逐个释放。
- 判断：fixture单槽held刻意只允许一个在途模型请求，产品无自动重试，出现第二请求应使测试失败而非队列放行。可记录明确unexpected request，不需要为异常重试适配Map。
- 证据：frontend/tests/settings.mjs:31

## OCR-1915 · 部分采纳

- 位置：frontend/tests/settings.mjs:49；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：无条件调用 `response.json()`：后端返回空 body、非 JSON 或经代理返回 HTML 错误页时，会先抛解析异常，`status` 断言永远不执行，把协议层失败伪装成 JSON 解析错误，并跳过其后的所有检查项。建议先 `await response.text()`，按 content-type/空串降级为 null 或原始文本，再一并返回状态码。
- 判断：frontend/tests/settings.mjs的loopback合成provider确在async handler直接解析JSON，异常缺统一失败响应/回执；采纳非法请求时快速、可诊断地失败及请求字节预算。此provider仅测试替身，不是产品公网协议漏洞。
- 证据：frontend/tests/settings.mjs:49

## OCR-1916 · 不采纳

- 位置：sdk/tests/examples.mjs:0；OCR原严重度：medium
- 计划：无
- 原意见：environment isolation 缺陷：被测子进程显式 `...process.env` 展开，把 CI 中的全部环境变量（云凭据、令牌、代理配置等）暴露给被测示例进程；provider 进程（第 8 行）更是完全不传 env，直接继承全量环境。这会污染被测行为（例如继承到的 API endpoint/代理导致请求外发），也扩大了凭据暴露面。建议显式白名单：仅传递 PATH/HOME/临时目录等必需变量，其余（JHARNESS_*）按需构造。
- 判断：测试宿主/SDK本身是受信任被测本机服务，继承运行环境不等同工具执行的白名单绕过；该文件显式覆写合成fixture凭据。未提供真实无关变量被记录或外发证据，不能把继承环境本身判为已发生泄漏。
- 证据：sdk/tests/examples.mjs:17

## OCR-1917 · 部分采纳

- 位置：sdk/tests/examples.mjs:13；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：此处的等待循环只轮询 ready 文本，没有任何 provider 'error' 事件处理：当 python 路径不可执行/脚本缺失时，spawn 会异步发出未捕获的 'error' 事件直接抛错（进程退出码非 0，finally 不会执行），或在无监听的情况下让循环空转满 10s 后才以 `error`（此时可能为空串）断言失败，诊断信息很弱。建议先注册 `provider.on('error', ...)` 记录失败原因并在循环条件中纳入，同时让第 7 行的 `assert.ok(python)` 给出明确提示（如 `assert.ok(python,'JHARNESS_PYTHON is required')`）。
- 判断：实际spawn未注册error事件，坏java/python可未捕获退出且无结构化fixture失败回执；采纳error/close监听与前置诊断。classpath缺失是Java非零退出非spawn error，原意见这部分不准确。
- 证据：sdk/tests/examples.mjs:13

## OCR-1918 · 部分采纳

- 位置：sdk/tests/examples.mjs:19；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：两点可复现缺陷：1) `child.kill()` 仅发 SIGTERM（Windows 上等同于强杀但 POSIX 下进程若忽略/阻塞清理则不退出），超时后仍无升级到 SIGKILL 的机制；2) 等待 `'exit'` 而非 `'close'`，Node 的 'exit' 可能在 stdio 未 flush 完时触发，导致第 20 行基于 output/stderr 的断言偶发失败（截断）。另外子进程 spawn 失败（ENOENT/权限）时 'exit' 永不触发，这里会无限挂起。建议：超时先 SIGTERM→宽限期→SIGKILL 并 reject，等待 'close'，并监听 'error'。
- 判断：sdk/tests/examples.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：sdk/tests/examples.mjs:19

## OCR-1919 · 部分采纳

- 位置：sdk/tests/examples.mjs:29；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：finally 中的清理不可终止：只发送 SIGTERM，随后无超时地等待 'exit'。若 provider 进程忽略信号、处于不可中断状态，或 spawn 本身失败（'exit' 永不触发、exitCode/signalCode 均为 null），测试进程会永久挂起而非失败退出，CI 只能靠外层超时被杀。建议在 finally 中加入 SIGKILL 兜底与超时拒绝（例如 Promise.race 一个 5s 超时），并在 provider spawn 处注册 'error' 监听以避免未捕获的 'error' 事件直接抛出。
- 判断：sdk/tests/examples.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：sdk/tests/examples.mjs:29

## OCR-1920 · 部分采纳

- 位置：sdk/tests/examples.mjs:9；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：输出缓冲无上界：ready/error（此处）与循环内的 output/stderr（第 18 行）都无限制累加字符串并在事件里隐式 Buffer→string 转换。若被测进程异常刷日志（或 provider 崩溃日志风暴），测试进程内存会持续增长甚至 OOM，且无法定位。建议设置上限（例如累计超过 1MB 后停止追加并记录截断标志），并对流 setEncoding('utf8') 避免分块截断多字节字符。
- 判断：sdk/tests/examples.mjs确逐块累加输出/请求字符串，运行时间有限不等于字节有界；采纳尾部日志预算、截断标记及UTF-8连续解码，防止失败诊断本身耗尽内存。尚无当前fixture异常输出的实际OOM证据。
- 证据：sdk/tests/examples.mjs:9

## OCR-1921 · 部分采纳

- 位置：sdk/tests/examples.mjs:20；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：脱敏断言只覆盖了子进程 stdout，未覆盖 stderr 和随后读取的会话日志。真正的敏感信息泄露路径恰恰在持久化层：`.jharness/sessions/*.jsonl`（第 23 行 facts）与 child.stderr 都可能原样落盘/输出 `synthetic-demo-key`，当前断言无法证明“落盘已脱敏”，此用例会给出一条虚假的绿灯。建议读取 facts 后同时对 facts、stderr 做密钥缺失断言（注意断言消息本身不要打印密钥值）。
- 判断：examples只查stdout无fixture key，已读取facts却不查stderr/事实JSONL；补这两种已知输出渠道缺secret断言。两条同根问题合并验收，不重复计修复任务。
- 证据：sdk/tests/examples.mjs:20

## OCR-1922 · 部分采纳

- 位置：sdk/tests/examples.mjs:23；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：会话日志读取后仅用于解析 fact，缺少对 facts 内容的密钥脱敏校验：`!facts.toString('utf8').includes('synthetic-demo-key')`。这是唯一能验证“敏感信息已落盘脱敏”的检查点，缺失后即便实现回退成明文写入，测试仍然 PASS。
- 判断：examples只查stdout无fixture key，已读取facts却不查stderr/事实JSONL；补这两种已知输出渠道缺secret断言。两条同根问题合并验收，不重复计修复任务。
- 证据：sdk/tests/examples.mjs:23

## OCR-1923 · 部分采纳

- 位置：sdk/tests/examples.mjs:24；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：会话日志为空文件时 `''.trim().split('\n')` 得到 `['']`，`JSON.parse('')` 抛出 SyntaxError，错误信息无法说明是“会话日志为空/未写入”还是协议问题。建议先断言内容非空再逐行解析，或过滤空白行。
- 判断：空facts会JSON.parse失败非零而非误PASS，补明确非空/JSONL行诊断；不应filter空行把未产事实变通过。
- 证据：sdk/tests/examples.mjs:24

## OCR-1924 · 不采纳

- 位置：scripts/run-workbench-matrix.mjs:16；OCR原严重度：high
- 计划：无
- 原意见：子进程环境未做白名单：`{...process.env}` 把父进程的**全部**环境变量（API Key、CI Token、代理凭据、云存储凭据等）原样注入被测脚本，仅删除了 `JHARNESS_BROWSER_CHANNEL` 一项。测试脚本一旦打印环境或把错误上下文写进 fixture 输出，就会把无关凭据持久化到 `.build-cache` 的 json/log 证据里（本脚本随后还会把这些内容写入矩阵证据）。建议改为显式允许列表构造 env，并强制覆盖浏览器相关变量；同时注意 `JHARNESS_MATRIX_*` 控制变量也不应透传给子进程（子进程可能误读）。
- 判断：矩阵启动受信任测试继承环境与插件工具白名单不同，fixture专用变量由各脚本覆写；未证实泄漏。强制剥离所有CI/凭据服务变量可能破坏原生验收。
- 证据：scripts/run-workbench-matrix.mjs:16

## OCR-1925 · 部分采纳

- 位置：scripts/run-workbench-matrix.mjs:20；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：超时/错误路径的资源生命周期不完整：(1) `child.once('error')` 使 Promise reject 时会跳过 `clearTimeout(timer)` 与 `writeFile(log,output)`，未清理的 5 分钟定时器会继续持有事件循环，且失败日志丢失、也没有写入 `failures`；(2) 定时器只发一次 SIGTERM，若子进程忽略/阻塞在无法处理的信号（例如卡在 D 状态或自行捕获 SIGTERM），`exit` 事件永不触发，`await` 会无限挂起；(3) 超时时长 `300000` 为硬编码，无法通过环境变量覆盖。建议 try/finally 清理定时器、超时后升级 SIGKILL 并给 kill 后的宽限期兜底、超时时长改由环境变量（带默认值）提供。
- 判断：spawn error reject跳过clearTimeout/日志，单次TERM无升级也可一直等exit。采纳finally清timer、error/close与有界树回收；固定5分钟本身不是bug，不必新增env控制。
- 证据：scripts/run-workbench-matrix.mjs:20

## OCR-1926 · 部分采纳

- 位置：scripts/run-workbench-matrix.mjs:19；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：子进程 stdout/stderr 被无界累积到内存：测试脚本输出大量日志（或异常刷屏、被死循环子进程刷日志）时会导致 OOM；随后又把原始输出**未脱敏**直接写盘，违反“敏感信息脱敏/有界数据处理”。建议设置累积上限（到阈值后截断并标记 `truncated`），并对输出做凭据/令牌模式脱敏后再写 log。
- 判断：矩阵输出无界字符串且原样写盘，采纳日志预算/截断和已知fixture凭据脱敏。未证实真实秘密已出现在输出，不照抄泛化模式删证据。
- 证据：scripts/run-workbench-matrix.mjs:19

## OCR-1927 · 部分采纳

- 位置：scripts/run-workbench-matrix.mjs:23；OCR原严重度：critical
- 计划：R0-01 / P2
- 原意见：Matrix evidence validation must not be bypassable: reject stale records by mtime/runId, require status PASS and fixtureCredentialsCleaned true, Array.isArray(checks) with expected count, and use content hash/unique names for screenshots with bytes written directly.
- 判断：record校验使用status PASS或cleaned的OR且不要求同run/具体checks，旧文件可消费；采纳按fixture版本schema验证同run完整成功与cleanup。旧settings确无status，迁移需兼容明确旧格式。
- 证据：scripts/run-workbench-matrix.mjs:23

## OCR-1928 · 部分采纳

- 位置：scripts/run-workbench-matrix.mjs:28；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：截图路径边界校验可被绕过：`resolve` 只做字符串规范化，不解析符号链接（`realpath`），缓存目录内的软链接（或测试写入的指向仓库外的 junction）可以让 `source` 的字符串前缀仍是 `cache/`，而实际 `readFile` 读到的是缓存目录之外的文件；`startsWith(cache+'\\')` 在 POSIX 上恒为 false，混用两种分隔符也说明该边界判断未按平台实现。建议先 `realpath(source)` 再与 `realpath(cache) + path.sep` 比较，并使用 `path.relative` 判定是否越界。
- 判断：截图resolve不解软链，采纳realpath根检查及唯一目标名；代码同时检查cache反斜杠和正斜杠两个前缀，POSIX并非恒拒，原意见这部分错误。
- 证据：scripts/run-workbench-matrix.mjs:28

## OCR-1929 · 采纳

- 位置：scripts/run-workbench-matrix.mjs:22；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：fail-fast 路径不会写出 `matrix.json`，导致上一轮的聚合报告残留：默认（未设置 `JHARNESS_MATRIX_KEEP_GOING=1`）时这里 `throw` 会跳过第 39 行的写入，上一次运行遗留的 `out/matrix.json`（status=PASS）仍留在磁盘上；同理 `mkdir(directory,{recursive:true})` 也不会清理各引擎目录，旧 `fixture.json`/截图与新记录混在一起。下游若只读取 `matrix.json`/目录内容，会把旧的 PASS 当作本次结果（违反“UNKNOWN 不得被重放”）。建议启动时清空或版本化 `out` 目录，并把失败推进 `failures` 后统一在 finally 中写出 status=FAILED 的矩阵报告再抛错。
- 判断：fail-fast throw发生matrix写入前，旧PASS会残留；应先标本run进行中，失败finally写FAILED/独立run目录，避免消费陈旧报告。
- 证据：scripts/run-workbench-matrix.mjs:22

## OCR-1930 · 采纳

- 位置：scripts/finalize-workbench-matrix.mjs:0；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：`paidRequests: 0` 是硬编码写入最终 PASS 产物的，本次校验从未读取记录里的 `paidRequests`/`runtime.paidRequests`，所以矩阵可以断言一个根本没有被验证过的成本约束（非法/未知付费计数的记录也能发布为 0）。建议从已校验记录聚合推导（并在上一处断言每记录为 0），而不是无条件写 0。
- 判断：finalize未读取每份paidRequests却常量0，不能据校验推出该字段；应校验每份实际成本来源为0再聚合，未知保持未知失败。
- 证据：scripts/finalize-workbench-matrix.mjs:20

## OCR-1931 · 部分采纳

- 位置：scripts/finalize-workbench-matrix.mjs:17；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：证据文件里的路径未做仓库边界校验就 join 到 root：`name`（sourceSha256 的键）和 `shot.path` 完全由 JSON 证据控制，`join(root,'../../../etc/passwd')` 会被规范化后逃出检出目录，脚本随之变成任意文件的 SHA-256 预言机（可用于确认敏感文件内容/做离线碰撞验证），绝对路径虽会被 join 前缀化，但 `../` 不受限。`scripts/run-workbench-matrix.mjs:28-29` 对截图有 `startsWith(cache)` 约束，此处把该保护丢掉了。建议 resolve 后强制 `abs===root||abs.startsWith(root+sep)`，不满足即失败。
- 判断：finalize对sourceSha256/shot.path join无根约束，采纳元数据路径realpath/角色目录限制；本地受管输入非远程哈希预言机接口，不扩大安全严重度。
- 证据：scripts/finalize-workbench-matrix.mjs:17

## OCR-1932 · 部分采纳

- 位置：scripts/finalize-workbench-matrix.mjs:15；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：Finalize must validate every retained record: checks are objects with passed:true and expected count/name, sourceSha256 is non-empty object, screenshots non-empty array, and runtime status/paidRequests/browserVersion fields match expected.
- 判断：finalize仅checks.length/every且source/screenshot默认空，缺完整schema/预期名称数量/状态/成本/version校验。采纳按fixture精确规范，历史字符串checks须显式旧schema迁移。
- 证据：scripts/finalize-workbench-matrix.mjs:15

## OCR-1933 · 部分采纳

- 位置：scripts/finalize-workbench-matrix.mjs:20；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：矩阵发布既非原子、也无失败失效处理，且忽略了既有矩阵的状态：1) 直接覆写 `out/matrix.json`，进程中断/磁盘满会留下截断但看起来是 PASS 的产物；2) 脚本从未读取 `out/matrix.json` 中 `scripts/run-workbench-matrix.mjs:39` 写入的 `status`/`failures`，一次 FAILED 运行的矩阵只要各记录文件仍自洽就会被改写成 PASS（UNKNOWN/FAILED 状态被重放）；3) 本脚本校验失败抛错时，上一轮遗留的 PASS `matrix.json` 仍原样留在目录里，会被 `scripts/collect-m1059-evidence.py:36` 当作最新证据消费。建议启动时先校验/删除旧矩阵，失败路径确保删除，最后用临时文件 + rename 原子发布。
- 判断：finalize原地覆盖且失败留旧matrix，需同run完整记录后原子发布；工具本意可恢复失败矩阵里已通过的有效记录，不应简单禁止所有曾FAILED父matrix。不得拼不同源码/JAR/fixture来源。
- 证据：scripts/finalize-workbench-matrix.mjs:20

## OCR-1934 · 部分采纳

- 位置：frontend/tests/questions.mjs:9；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：第 9 行直接用 `join(process.env.JAVA_HOME, ...)` 组装 java 路径，但没有校验 JAVA_HOME 是否存在。触发条件：CI 未设置 JAVA_HOME 时 `path.join(undefined, ...)` 抛 `The "paths[0]" argument must be of type string`；此时 `start()` 尚未执行到 `host=spawn(...)`，全局 `host` 仍为 undefined，随后第 105 行 `host.exitCode===0` 又抛 TypeError，把真正的错误信息掩盖掉（只看到一个与根因无关的空指针）。建议在文件头 fail-fast 校验 JAVA_HOME 并在 spawn 处判空。
- 判断：frontend/tests/questions.mjs直接从JAVA_HOME构造JDK路径，确无缺项/文件存在前置提示；采纳清楚的fixture前置失败回执。CI已设置JDK，缺变量失败不是正常验收误通过，不采纳为高严重度产品缺陷。
- 证据：frontend/tests/questions.mjs:9

## OCR-1935 · 部分采纳

- 位置：frontend/tests/questions.mjs:36；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 36 行以 `env:{...process.env, ...}` 启动宿主进程，宿主会继承 CI/开发者环境里的全部变量（各类 token、云凭据、代理配置等），而这些变量与本 fixture 无关；宿主的插件/工具子进程同样可以读到。建议改成显式白名单，只传 `PATH`、`JAVA_HOME`、`JHARNESS_WEB_TOKEN`、`JHARNESS_QUESTION_KEY`（以及确有必要时的 `TMPDIR`）。 另外同一行还有一处可复现的竞态：`base=output.match(/WEB (http:[/][/]127...:[0-9]+)/)?.[1]` 每收到一个 stdout 分片就匹配一次，如果 `WEB http://127.0.0.1:PORT` 正好在端口数字中间被切分，正则会把截断后的端口（如 4 位端口的后两位）当作完整 URL 写入 `base`，而 `until(()=>base||...)` 会立即返回，后续 `api()` 全部打到错误的端口。建议按行缓冲（`output.split('\n')` 且只取以换行结尾的完整行）后再匹配。
- 判断：questions stdout正则不要求整行结束，端口数字分块可先识别前缀并提前readiness；采纳按完整WEB行解析。继承受信测试环境不等同凭据漏洞。
- 证据：frontend/tests/questions.mjs:36

## OCR-1936 · 部分采纳

- 位置：frontend/tests/questions.mjs:18；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：provider 是 async 请求处理函数，但第 17-18 行的 `for await(const c of req)`、`JSON.parse(raw)` 与 `body.messages.findLast(...)` 都没有 try/catch 和字段判空。触发条件：请求体不是合法 JSON、连接被中断、或 body 缺少 messages/tool_call_id 时，处理函数返回 rejected promise；Node 15+ 默认在 unhandledRejection 时终止进程，表现为 harness 在没有具体断言的情况下挂死或报 `Question fixture timeout`，根因被吞掉。另外 `m.tool_call_id.startsWith` 在 tool 消息缺字段时也会抛 TypeError。建议包一层 try/catch，非法请求回 400 并记录原因，同时对 `body.messages??[]` 做判空。
- 判断：frontend/tests/questions.mjs的loopback合成provider确在async handler直接解析JSON，异常缺统一失败响应/回执；采纳非法请求时快速、可诊断地失败及请求字节预算。此provider仅测试替身，不是产品公网协议漏洞。
- 证据：frontend/tests/questions.mjs:18

## OCR-1937 · 部分采纳

- 位置：frontend/tests/questions.mjs:103；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：清理逻辑是串行的裸 await，缺少 try/catch，存在资源泄漏与挂死风险：1) `browser.close()` 或 `stop()` 抛错时（`stop()` 内含 `assert.equal(host.exitCode,0)` 和 30s 超时的 `until`），`provider.close(r)` 会被跳过，HTTP 服务仍在监听，Node 事件循环不会退出，CI 最终只能等外层超时；同时抛出的清理异常会覆盖原始失败原因。2) 没有调用 `provider.closeAllConnections()`（对比 development.mjs:55 的写法），而 Java HttpClient 默认 keep-alive，`server.close()` 会一直等待存量连接；第 31 行 `draft-before-question` 场景还会把请求一直挂起直到 `releaseQuestion()` 被调用，若用例在此之前失败，该连接永不结束，`close` 回调永远不触发。建议每个清理步骤独立 try/catch 记录，并在 `close()` 之前 `provider.closeAllConnections()`。
- 判断：frontend/tests/questions.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：frontend/tests/questions.mjs:103

## OCR-1938 · 部分采纳

- 位置：frontend/tests/questions.mjs:82；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：敏感信息脱敏的覆盖范围过窄，有两个盲点：1) 这里只检查 localStorage/sessionStorage，没有检查草稿请求体——第 49 行的 `page.on('request')` 只记录以 `/answer` 结尾的 POST，`/questions/*/draft` 的请求负载完全没被抓取，因此“原文密钥被写进草稿请求/宿主草稿文件”这类泄漏不会被发现（本用例只验证了刷新后 UI 显示 `[REDACTED]`）；2) 第 85 行 `wire.filter(w=>w.goal==='private').every(...)` 在过滤结果为空数组时恒为真（vacuous truth），一旦 private 场景的 provider 请求因任何原因没被记录，这条脱敏断言会静默通过。建议新增对 draft 请求体的捕获并断言其中不含 secret，且在第 85 行先断言 `wire.some(w=>w.goal==='private')`。
- 判断：private.every空集合存在局部覆盖缺口，应要求该场景请求确出现；草稿请求将用户原文发本机是设计，脱敏发生宿主内存草稿服务，不应禁止secret出现在本机POST。补存储草稿/下一模型请求脱敏即可。
- 证据：frontend/tests/questions.mjs:82

## OCR-1939 · 不采纳

- 位置：frontend/tests/questions.mjs:24；OCR原严重度：low
- 计划：无
- 原意见：第 24 行是嵌套三元表达式（`? :` 里再嵌 `? :`），违反项目“禁止嵌套三元”的规定，且在流程/模式矩阵扩展时很难读。建议先取局部常量再判断。
- 判断：frontend/tests/questions.mjs是固定步骤的受控fixture；精确次数用于锁定请求/审批契约，嵌套条件不产生已证实运行错误。未来新增步骤需同步断言不是当前缺陷，不能仅按OCR风格规则重写。
- 证据：frontend/tests/questions.mjs:24

## OCR-1940 · 不采纳

- 位置：frontend/tests/questions.mjs:59；OCR原严重度：medium
- 计划：无
- 原意见：`draftCalls===1` 只能证明“此刻只发出过一次草稿请求”，无法区分下面两种完全不同的实现：(a) 第二次保存被正确串行化排在第一次之后；(b) 第二次输入根本没有触发保存（例如误加了防抖/丢请求）。当前写法在发生 (b) 回归时同样通过。触发条件：任意改动前端草稿保存策略。建议在 `releaseDraft()` 之后补充 `await until(()=>draftCalls===2)`，并断言服务端最终草稿为第二次输入的内容。
- 判断：releaseDraft后测试等待草稿已保存并刷新，紧接断第二次文本Keep separate approval已恢复；丢第二次保存会使后续明确失败。不能只截draftCalls===1忽略整体覆盖重复加测试。
- 证据：frontend/tests/questions.mjs:59

## OCR-1941 · 采纳

- 位置：frontend/tests/questions.mjs:64；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：这里的断言读的是 `note.txt`，但第 27 行 provider 排队的写操作目标是 `must-not-write.txt`，内容 `requires separate approval` 永远不会出现在 `note.txt`（其中只有 `question evidence`）。触发条件：`ask_user` 回答错误地自动批准/执行了排队中的 write_file——目标文件被真正写出时，该断言依然为真，只有 `approvals.length===1` 这一半在起作用（而且审批在下一行就被“拒绝”了）。这样“回答不会越权批准写工具”这条关键安全断言实际上是空的。建议直接断言目标文件不存在/未被写入。
- 判断：provider写入目标是must-not-write.txt，而断言读取note.txt，后者内容固定question evidence；不能发现实际写入目标被越权执行。保留approvals.length检查并直接断言目标不存在。
- 证据：frontend/tests/questions.mjs:64

## OCR-1942 · 采纳

- 位置：frontend/tests/questions.mjs:77；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：跳过的断言只校验了 `state==='SKIPPED'`，没有校验推荐项是否被排除。触发条件：前端/后端在点击“跳过问题”时把推荐项 `selected:['a']` 一并提交，状态仍是 SKIPPED，本用例照样通过——而这正是这一条要防的回归。建议同时断言 `selected` 为空，并校验实际发出的跳过请求体里 `skip===true`（注意第 46 行 `body()` 的 `skip` 形参从未传入 true，属于未使用参数，跳过路径的请求体结构因此完全没有被验证）。
- 判断：skip后只检查SKIPPED，未检查posts里的skip/selected或持久selected为空，确弱于推荐项未提交的检查名称。补真实请求体与持久结果断言。
- 证据：frontend/tests/questions.mjs:77

## OCR-1943 · 采纳

- 位置：frontend/tests/questions.mjs:92；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`!e.disabled` 只验证了单选框未被禁用，与“键盘可达”无关。触发条件：控件被加上 `tabindex="-1"`、被 `pointer-events:none` 之外的焦点陷阱包裹、或被 CSS `visibility`/`display` 之外的焦点样式屏蔽时，该断言仍为真。建议真正 focus 后断言 `document.activeElement===e`，再用 Tab 键验证焦点能进入选项组。
- 判断：radio键盘可聚焦检查实际仅!e.disabled，没有focus/Tab或activeElement；应执行真实键盘操作，不能把未禁用等同键盘可达。
- 证据：frontend/tests/questions.mjs:92

## OCR-1944 · 采纳

- 位置：frontend/tests/questions.mjs:95；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：重启场景里直接 `host.kill()`，但没有像 `stop()`（第 38 行）那样先 `stoppedOrigins.add(base)`。触发条件：宿主被杀后页面仍指向旧 origin，前端进行中的读取会被判为 transport 诊断；由于 `stoppedHostDiagnostic(e.message, stoppedOrigins)` 中不含该 origin（参见 runtime.mjs:14-24 的实现），这些预期内的诊断会进入 `errors`，让第 102 行 `no browser runtime exceptions` 偶发失败。建议在 kill 之前加入 `stoppedOrigins.add(base)`。
- 判断：重启分支host.kill前没有像stop一样登记stoppedOrigins，受控旧主机只读诊断会被记unexpected errors。应只在这次受控停机前登记精确origin，保持其它异常失败。
- 证据：frontend/tests/questions.mjs:95；frontend/tests/runtime.mjs:14

## OCR-1945 · 部分采纳

- 位置：sdk/tests/run.mjs:23；OCR原严重度：high
- 计划：R0-03 / P3
- 原意见：`server.listen`、`mkdir(recursive)` 以及紧随其后的 `execFileSync(jar ...)` 都位于 `try` 之外，因此任何一步失败都会绕过 finally 的 `closeAllConnections()/server.close()`： - `JAVA_HOME` 未设置时 `join(process.env.JAVA_HOME,'bin',...)` 直接抛 TypeError； - `harness-testkit/target/test-classes` 或 `harness-cli` jar 不存在时 `execFileSync` 抛 ENOENT。 此时 HTTP listener 仍然保持打开，事件循环无法清空，脚本挂住不退出（CI 表现为超时而非失败）。建议把 `listen` 之前的准备工作放到 try 内部，`listen` 之后再进入 try，并保证 finally 覆盖所有已获取资源。
- 判断：sdk/tests/run.mjs直接从JAVA_HOME构造JDK路径，确无缺项/文件存在前置提示；采纳清楚的fixture前置失败回执。CI已设置JDK，缺变量失败不是正常验收误通过，不采纳为高严重度产品缺陷。
- 证据：sdk/tests/run.mjs:23

## OCR-1946 · 不采纳

- 位置：sdk/tests/run.mjs:35；OCR原严重度：medium
- 计划：无
- 原意见：把 `process.env` 全量透传给被 spawn 的 TypeScript/Python worker，会把与此测试无关的凭据（云平台 token、`OPENAI_API_KEY`、代理凭据、`NODE_OPTIONS`/`PYTHONPATH` 等可影响执行的变量）一并交给子进程及其派生的 JVM；worker 崩溃时还会把这些环境相关的输出写进日志。建议只显式传递白名单变量（PATH/PATH 相关的 JDK 变量、JHARNESS_* 以及必要的 locale/临时目录），其余丢弃。
- 判断：子worker是仓库受信的TS/Python测试而非模型提供代码，完整继承环境是普通测试进程语义；夹具自行注入随机synthetic key并将工具命令环境交由生产白名单。未证明无关环境值被输出/泄漏，不泛化要求所有本机测试子进程最小环境。
- 证据：sdk/tests/run.mjs:35

## OCR-1947 · 部分采纳

- 位置：sdk/tests/run.mjs:11；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：这个 async 请求处理器没有任何 try/catch，且对请求体不设上限： 1) `JSON.parse(raw)` 遇到畸形/空 body 会抛错，`body.messages` 缺失时 `body.messages.findLast` 会抛 TypeError，`for await (const chunk of req)` 在客户端中途取消时会 reject；这些异常都会变成未捕获的 unhandledRejection（Node 18+ 默认终止进程），导致整个 harness 崩溃，而客户端只能看到连接被断开，排查非常困难。 2) `raw` 无长度上限地累加，单个恶意/异常请求即可耗尽内存。 建议捕获解析异常与读取异常，限制累计长度，并在每个分支都写回响应（包括 400/413）。
- 判断：sdk/tests/run.mjs的loopback合成provider确在async handler直接解析JSON，异常缺统一失败响应/回执；采纳非法请求时快速、可诊断地失败及请求字节预算。此provider仅测试替身，不是产品公网协议漏洞。
- 证据：sdk/tests/run.mjs:11

## OCR-1948 · 部分采纳

- 位置：sdk/tests/run.mjs:42；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：这里只监听 `exit`，没有监听 `error`：当 `spawn` 因 ENOENT/EACCES 失败（例如 `JHARNESS_PYTHON` 指向不存在的路径、`process.execPath` 被替换）时 Node 只发出 `error` 而不发 `exit`，该 Promise 永不 resolve，配合 120s 定时器里的 `child.kill()` 只会让流程继续挂住或抛出新的错误。另外 `child.kill()` 默认只发 SIGTERM，没有 SIGKILL 兜底：worker 内部已经启动的 JVM/peer 进程若忽略 SIGTERM，会成为孤儿进程一直存活到 CI 结束，并可能继续持有端口/写文件，污染后续场景。建议 spawn 后立即注册 `error` 监听使等待 Promise 结束，并在超时后升级为 `kill('SIGKILL')`（必要时按进程组杀）。
- 判断：sdk/tests/run.mjs实际stop/finally串行清理且kill缺有界升级/等待，某步抛错会遮盖原错误或跳过后续资源；采纳独立清理、close/error双监听、TERM→KILL有界回收及原始失败保留。仅针对自有fixture进程，不按历史PID清理。
- 证据：sdk/tests/run.mjs:42

## OCR-1949 · 不采纳

- 位置：sdk/tests/run.mjs:48；OCR原严重度：medium
- 计划：无
- 原意见：结果工件中的 `status:'PASS'`、`paidRequests:0`、`allAuthorized:true`、`modelContextRedacted:true` 是硬编码常量，并非由实际数据推导：一旦下游/后续重构跳过或放宽了上面的断言（甚至 `wire` 为空、收到未鉴权请求），仍然会写出“全部通过、零付费请求、上下文已脱敏”的凭据性工件，把失败记录当成成功证据；同时 `paidRequests:0` 这个关键合规指标完全没有被实际度量（未统计 baseUrl 是否指向回环地址以外的端点）。建议由 `wire` 计算这些字段（如 `allAuthorized:wire.length>0&&wire.every(w=>w.authorized)`），并用实际统计值替代字面量。
- 判断：wire.every鉴权和请求正文脱敏断言就在报告前，失败即抛错；provider配置固定127.0.0.1、不转发，常量是构造性结论不是发生付费的证据。没有证明断言被跳过的当前路径，不据未来删断言假设立漏洞。
- 证据：sdk/tests/run.mjs:48

## OCR-1950 · 不采纳

- 位置：sdk/tests/run.mjs:32；OCR原严重度：low
- 计划：无
- 原意见：把 `secret`（即 `JHARNESS_API_KEY`）以明文写进仓库目录下的 `.build-cache/**/large.txt`，且整个流程没有清理逻辑：失败或中断后该目录、失败日志以及 peerJar 都会永久残留，密钥随工作目录被打包/提交/缓存的风险很高（`.build-cache` 是否在 .gitignore 之外还取决于外部配置）。此外失败日志文件名不含运行时间戳，成功重跑后旧的失败日志仍会被误认为本次结果。建议使用 `os.tmpdir()` 下的临时目录并以 `try/finally` 递归删除，或至少在 finally 中清除含密钥的文件。
- 判断：secret由synthetic-sdk-加randomUUID生成，不是真实JHARNESS_API_KEY；故意写large.txt用于产物脱敏反例，.build-cache受Git忽略且保留失败证据是项目约定。原意见将合成密钥误当真实密钥并要求删除取证目录。
- 证据：sdk/tests/run.mjs:32

## OCR-1951 · 部分采纳

- 位置：sdk/tests/run.mjs:45；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：Parse worker reports from last parseable JSON line and validate checks array non-empty and passed true; assert wire is non-empty and per-scenario ordering/counts rather than vacuous every().
- 判断：JSON.parse最后一行是测试客户端明确输出契约，解析最后任意可解析JSON反而可能吞错。采纳run层明确校验两语言report.checks非空/通过以及wire非空，提高独立运行收据完整性；逐场景顺序已有客户端断言，非目前零调用已通过的事实。
- 证据：sdk/tests/run.mjs:45

## OCR-1952 · 部分采纳

- 位置：frontend/tests/search.mjs:10；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：`process.env.JAVA_HOME` 未做空值校验：环境变量缺失时 `join(undefined,...)` 在模块顶层抛 “Path must be a string”，错误信息与真实原因（缺少 JAVA_HOME）无关，且发生在任何 try/catch 之前。建议先显式校验并给出可读错误。
- 判断：frontend/tests/search.mjs直接从JAVA_HOME构造JDK路径，确无缺项/文件存在前置提示；采纳清楚的fixture前置失败回执。CI已设置JDK，缺变量失败不是正常验收误通过，不采纳为高严重度产品缺陷。
- 证据：frontend/tests/search.mjs:10

## OCR-1953 · 部分采纳

- 位置：frontend/tests/search.mjs:50；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：脱敏断言覆盖面不足：这里只对第 1 页的 `first` 快照做 `JSON.stringify(...).includes(secret)` 检查，而 `next`（第 2 页）、`view:'all'` 全量页、以及后续重建索引后的结果都没有检查。若派生索引/快照只在后续页或重建后泄漏原始 goal，本用例仍会通过。建议对所有页（遍历 next 直到为空）与所有 scope 的响应体统一做敏感值扫描。
- 判断：首批first显式查无secret，next/归档all/重建响应没有统一扫描；采纳对各已读取分页/范围的响应统一敏感值断言，不认为已有source片段脱敏实现被证明泄漏。
- 证据：frontend/tests/search.mjs:50

## OCR-1954 · 部分采纳

- 位置：frontend/tests/search.mjs:0；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：模型侧唯一记录的是 `raw.includes(secret)`，未检查 `JHARNESS_WEB_TOKEN`（以及任何其它凭据）是否出现在请求体/提示词/非授权请求头中；第 73 行 `wire.every(w=>w.authorized&&w.redacted)` 因此只能证明 API key 未泄漏，无法证明 Web 会话令牌未被打进模型请求。建议在 mock 中同时检查 `token` 与 `authorization` 头是否被错误透传（例如记录 `raw.includes(token)`、`req.headers` 中是否出现 `Bearer `+token）。
- 判断：mock只记录raw.includes(secret)，未记录raw是否含Web token；采纳把固定token纳入敏感集合并核对provider header只为synthetic provider key，不声称已观察到token透传。
- 证据：frontend/tests/search.mjs:1

## OCR-1955 · 不采纳

- 位置：frontend/tests/search.mjs:65；OCR原严重度：medium
- 计划：无
- 原意见：“打开来源不应提交任务”的判定存在竞态：只等待“继续会话”区域出现就立刻读 `wire.length`，若点击确实误触发了任务提交，其模型请求是异步发出的，此刻计数仍是 39，断言会误通过。另外 39 是硬编码的任务总数，任务数变化即失效。建议点击前后各取一次计数（并在等待一个稳定的短轮询窗口或直接比对 `/v1/tasks` 的 historyCount 未增加）后再断言，不要依赖固定常量。
- 判断：frontend/tests/search.mjs是固定步骤的受控fixture；精确次数用于锁定请求/审批契约，嵌套条件不产生已证实运行错误。未来新增步骤需同步断言不是当前缺陷，不能仅按OCR风格规则重写。
- 证据：frontend/tests/search.mjs:65

## OCR-1956 · 部分采纳

- 位置：frontend/tests/search.mjs:25；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：`path.replace(root+'\\','')` 只在 Windows 生效；脚本在 POSIX（Linux/macOS CI，且脚本本身已按 `process.platform` 处理 java 可执行名）下会把绝对路径写进结果 JSON，泄漏构建机路径且与 Windows 结果不一致。建议改用 `relative(root,path)`。
- 判断：frontend/tests/search.mjs截图相对路径使用root加反斜杠替换，POSIX无法剥离；采纳path.relative再统一分隔符保证可搬运证据。绝对checkout路径本身不是密钥泄漏，不采纳高风险表述。
- 证据：frontend/tests/search.mjs:25

## OCR-1957 · 部分采纳

- 位置：frontend/tests/search.mjs:31；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：工作区注册未校验状态码与响应结构：注册失败（4xx/5xx 或返回错误体）时 `registry.workspaces` 为 undefined，下一行 `.find(...)` 直接抛 TypeError，真实原因（注册被拒/路径越界）被掩盖；且 `.find().id` 在找不到时同样抛错。建议先 `assert.equal(r.status,200/201)`，再断言找到的目标工作区存在（按名称匹配而非仅 `id!=='default'`）。
- 判断：工作区注册直接取body.workspaces.find，错误时fail-closed但只剩TypeError；采纳状态码/命名目标存在断言以保留真正拒绝原因，非假绿漏洞。
- 证据：frontend/tests/search.mjs:31

## OCR-1958 · 采纳

- 位置：frontend/tests/search.mjs:69；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：XSS 用例存在两处可误通过的窗口： 1) 竞态：`until(async()=>!await ...isVisible())` 在点击后立即执行，此时“正在检索…”往往尚未渲染，条件首轮即为 true，断言在请求返回前就结束；即便请求失败（500）也照样通过。 2) 断言强度不足：脚本只把 `<script>` 通过 `fill()` 填入输入框（纯文本输入，天然不会执行），且仓库中没有任何含 HTML 的已存 goal/snippet，因此即使前端用 innerHTML 渲染结果/查询词，`window.__search_xss` 仍为 undefined —— `<script>` 经 innerHTML 插入本身不执行。该用例无法验证转义。 建议：等待最终计数文案后再断言；并额外断言结果区域不存在 `script` 元素、渲染文本中 `<script>` 以字面量出现；同时构造一条 goal 含 `<img src=x onerror=...>` 的任务再检索，以真正覆盖 snippet 转义路径。
- 判断：XSS场景只fill <script>查询且立即等待加载标记不可见，可能还未进入/完成请求；innerHTML插script本来也不触发执行。需持久带img/onerror的命中正文、等待最终结果并检查DOM/文本/脚本未执行。
- 证据：frontend/tests/search.mjs:69

## OCR-1959 · 部分采纳

- 位置：frontend/tests/search.mjs:56；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：该恢复用例有两个问题： 1) 服务仍在运行，直接覆写内部缓存文件（`.jharness/content-search/<id>.content.json`）没有与服务的写入同步，服务若并发重写该文件，用例会抖动；同时断言硬绑了内部文件布局，布局变化即为假失败。 2) 断言只校验 `rebuiltSessions===1` 与源 JSONL 未变，未校验重建后的索引内容 —— 若重建产出一个空索引/错误索引，`rebuiltSessions` 仍为 1，用例照样通过。 建议：损坏后除断言 `rebuiltSessions===1` 外，再断言该会话检索命中数/snippet 正确（如 total===1 且包含 'boost'），从而真正验证“重建可用”而非“重建被触发”。
- 判断：损坏索引后只核rebuiltSessions及事实文件未变，未核该响应命中内容。采纳同会话boost结果/数量断言；在无并发检索步骤时直接损坏缓存是有意故障注入，不要求取消此反例或承诺当前已有竞态。
- 证据：frontend/tests/search.mjs:56

## OCR-1960 · 部分采纳

- 位置：frontend/tests/turns.mjs:11；OCR原严重度：high
- 计划：R0-03 / P3
- 原意见：第 11 行在模块顶层直接解引用 `process.env.JAVA_HOME`：未设置时 `join(undefined, ...)` 抛出 `TypeError: Path must be a string`，发生在第 69 行的 try 之前，因此 catch/finally 都不会执行，既不会写 `M10.5.3-turns-failure.txt`，也不会 `kill` 子进程/关闭浏览器。同理第 32-34 行（`mkdir`、写 `note.txt`/`fixture.json`、`provider.listen`）也在 try 之外，任何 IO/端口失败都会静默中断且无诊断产物。建议显式校验 JAVA_HOME（缺失时给出明确报错）并把环境准备移入 try/catch，或提前安装 process 级 `unhandledRejection`/`uncaughtException` 兜底。
- 判断：frontend/tests/turns.mjs直接从JAVA_HOME构造JDK路径，确无缺项/文件存在前置提示；采纳清楚的fixture前置失败回执。CI已设置JDK，缺变量失败不是正常验收误通过，不采纳为高严重度产品缺陷。
- 证据：frontend/tests/turns.mjs:11

## OCR-1961 · 不采纳

- 位置：frontend/tests/turns.mjs:34；OCR原严重度：high
- 计划：无
- 原意见：第 35 行的 `env` 把整个 `process.env` 透传给两个子 JVM（第 38 行 `start()` 的 host、第 67 行 `maintain()` 的 CLI）。被测试的 host/CLI 因此可读到与本次用例无关的 CI 凭据（其他服务 token、云密钥等），违反“环境变量白名单”要求；同时 `JHARNESS_TURN_KEY`/`JHARNESS_WEB_TOKEN` 之外的环境变量也会影响被测进程行为，降低可复现性。建议改为显式白名单。
- 判断：测试宿主/SDK本身是受信任被测本机服务，继承运行环境不等同工具执行的白名单绕过；该文件显式覆写合成fixture凭据。未提供真实无关变量被记录或外发证据，不能把继承环境本身判为已发生泄漏。
- 证据：frontend/tests/turns.mjs:34

## OCR-1962 · 部分采纳

- 位置：frontend/tests/turns.mjs:19；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：fixture provider 的 async 请求处理器没有任何异常兜底：1) `JSON.parse(text)` 与 `body.messages.findLastIndex` 未做 try/catch，任何非 JSON/非 OpenAI 形状的探测请求（含被 abort 的半截请求体）都会 reject 该 handler 的 Promise，Node 对 server listener 的 rejected promise 不会自动捕获，可能触发 unhandled rejection 直接终止进程，留下未关闭的 JVM 与浏览器；2) 请求体 `text` 无大小上限，违反“有界数据处理”；3) `last === -1`（无 user 消息）时 `slice(0)` 会把 system 消息也算进 toolsDone，且 `content` 为非字符串（多模态数组）时 `String(goal)` 得到 `[object Object]`，slow-* 分支判定随之失效。建议 try/catch 包住整个处理器、限制累积长度并校验 `messages`/`last`。
- 判断：frontend/tests/turns.mjs的loopback合成provider确在async handler直接解析JSON，异常缺统一失败响应/回执；采纳非法请求时快速、可诊断地失败及请求字节预算。此provider仅测试替身，不是产品公网协议漏洞。
- 证据：frontend/tests/turns.mjs:19

## OCR-1963 · 采纳

- 位置：frontend/tests/turns.mjs:52；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：`events()` 的分页循环不校验 HTTP 状态、不限制迭代次数：1) 服务返回非 200 的错误体（如 `{error:...}`）时 `value.events` 为 undefined，`rows.push(...undefined)` 抛 `TypeError`，报错信息与真实原因无关；2) 服务返回 `more:true` 但 `next` 未前进（重复游标）时 `after` 始终为同一值，循环永不结束，整个用例挂死而非有界失败；3) `more:true` 而 `next` 为空串时会静默截断事件列表，后续基于事件的断言（如 turn 数、UNKNOWN 状态）会给出误导性结论。建议加状态断言、游标必须前进的断言以及最大页数上限。
- 判断：events do/while确无status、页数上限、游标重复集合；同next+more可在until内部无限await，外层until也不能超时。加每页状态/结构、前进/页数断言，使产品分页回归有界失败。
- 证据：frontend/tests/turns.mjs:52

## OCR-1964 · 不采纳

- 位置：frontend/tests/turns.mjs:41；OCR原严重度：high
- 计划：无
- 原意见：`stop()` 使用 `until` 的默认 30s 预算，而 fixture 配置声明 `requestTimeoutSeconds: 80`、`durationSeconds: 90`（第 34 行）。第 114-115 行在 `slow-exit` 请求被 hold 未返回时调用 `stop()`，一个“优雅关闭并等待在途请求”的 host 可能需要接近 80s 才能退出，30s 后 `until` 抛 `Turn fixture condition timed out`，导致第 118 行之后的 UNKNOWN 恢复断言（含“不得重放”“PAUSED 到显式开始”）全部被跳过，失败原因也被误报为超时。建议为 `stop()` 显式传入大于 requestTimeout 的预算（并保留失败时的 kill 兜底）。另外 `stoppedOrigins.add(base)` 在 `base` 为 undefined 时会把 undefined 加入集合。
- 判断：宿主close按显式有界取消/关闭流程退出，不应为等到80秒模型HTTP timeout才算正确；30秒退出夹具是合理门限。不能按模型requestTimeout直接推导合法关闭需要80秒，也不建议把异常停机等待加长掩盖生命周期回归。
- 证据：frontend/tests/turns.mjs:41

## OCR-1965 · 采纳

- 位置：frontend/tests/turns.mjs:36；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`start()` 每次都以 `output = ''` 清空 host 输出，而第 128 行的 `outputRedacted: !output.includes(key)` 只检查**最后一次**启动的 stdout/stderr：第一批启动的 host（创建任务、草稿、lost-response 各阶段）以及第 122-123 行 `maintain()` 的 CLI 输出（只进局部 `text`）完全不在脱敏检查范围内，凭据一旦在这些阶段被打印，安全检查仍会 PASS。建议改为累积到独立缓冲区（如 `allOutput`）并整体参与校验，或对每次进程输出单独执行脱敏断言。
- 判断：start确output清空，最终outputRedacted只覆盖最后宿主；maintain输出只局部text并仅失败时输出，没有成功输出脱敏断言。应各次宿主/维护命令结算扫描或汇总到独立有界安全缓冲，保持原秘密不输出。
- 证据：frontend/tests/turns.mjs:36

## OCR-1966 · 不采纳

- 位置：frontend/tests/turns.mjs:136；OCR原严重度：low
- 计划：无
- 原意见：`replaceAll(key,'[REDACTED]').replaceAll(token,'[REDACTED]')` 这段脱敏链在本文件里重复了 5 处（第 67、128、133-134、136 行等）。任何一处漏改都会造成凭据外泄，而新增敏感值时也容易只改其中几处。建议抽出 `const redact = s => String(s).replaceAll(key,'[REDACTED]').replaceAll(token,'[REDACTED]')` 统一调用。
- 判断：重复replaceAll是小规模测试诊断处理，未指出现有任一调用漏掉实际key/token；抽函数可随相关改动做，无需独立修复/测试阶段，不将重复本身当凭据外泄。
- 证据：frontend/tests/turns.mjs:136

## OCR-1967 · 不采纳

- 位置：frontend/tests/turns.mjs:118；OCR原严重度：medium
- 计划：无
- 原意见：这里直接解引用 `holds.get('slow-exit').response` 而不做存在性检查：若该 key 不存在（例如前一步的请求因重启/重试走的是别的分支），这里抛出 `TypeError: Cannot read properties of undefined`，掩盖真实原因。更重要的是 `holds` 表从未删除条目、且第 29 行在收到同 goal 请求时会覆盖旧值，于是第 93 行与第 114 行的 `until(() => holds.has('slow-...'))` 可能被上一轮遗留的条目瞬间满足，导致“busy/停服时确有在途请求”的前提没有被真正验证；被覆盖的旧 response 也不会被结束，直到 finally 的 `closeAllConnections` 才释放。建议发送后 `holds.delete(goal)`，并在使用前断言条目存在。
- 判断：slow-current与slow-exit各在本次运行创建一次，调用holds.has后才get；没有原意见所谓同goal上一轮条目使前提瞬间满足的当前执行路径。缺项会明确使测试失败，不是产品恢复被误计通过。
- 证据：frontend/tests/turns.mjs:118

## OCR-1968 · 不采纳

- 位置：frontend/tests/turns.mjs:24；OCR原严重度：medium
- 计划：无
- 原意见：`send()` 只用 `response.destroyed` 判定可写性：如果同一个 hold 被释放两次（第 29 行覆盖 hold、或调用方重复调用 `send()`），第二次 `writeHead/end` 会抛 `ERR_STREAM_WRITE_AFTER_END`，在 async 请求处理器里变成 unhandled rejection。此外第 29 行 `holds.set` 覆盖已有条目时，旧 response 永远不会被结束，socket 一直挂到 finally 的 `closeAllConnections`，会干扰“在途请求数量/停服时刻”的判定。建议同时判断 `writableEnded`，并把 hold 改为一次性消费。
- 判断：send在当前流程仅对slow-current释放一次，slow-exit故意不回响应而由停服销毁；没有二次send/相同goal覆盖的执行步骤。以假想后续重复调用要求一次性容器，不作为现有缺陷。
- 证据：frontend/tests/turns.mjs:24

## OCR-1969 · 不采纳

- 位置：frontend/tests/turns.mjs:120；OCR原严重度：high
- 计划：无
- 原意见：`pendingRequest` 由 `.at(-1)` 取得且未做任何校验：1) 若 session 里没有 `model.requested` 事件，`pendingRequest` 为 undefined，下一行 `pendingRequest.eventId` 直接抛 TypeError；2) 更关键的是这里默认“最后一条 model.requested 就是待定/UNKNOWN 的那条”，一旦顺序或恢复流程变动，`verify-request` 会用合成理由给一个**已经被应答过**的请求做 UNKNOWN 认定，等于把人工作废确认签在了错误的事件上，后续“UNKNOWN 不得被重放”的断言也就失去意义。建议断言其存在，并校验其后没有对应的响应/工具事件（确实处于 pending 状态）。
- 判断：最后一次slow-exit明确被hold且stop后验证socket.destroyed，后续没有新模型请求；CLI verify-request本身核对事件是否仍pending，已结算事件会拒绝。缺id会失败，不会给已答请求静默作废确认。
- 证据：frontend/tests/turns.mjs:120

## OCR-1970 · 不采纳

- 位置：frontend/tests/turns.mjs:119；OCR原严重度：high
- 计划：无
- 原意见：`id` 来自第 74 行的 `sessionStorage.getItem('jh-selected')`，全程未做格式校验，却被用于：此处拼接 `.jharness/sessions/${id}.jsonl` 的读取路径、`api(taskPath(...))` 的 URL，以及第 122-123 行的 `--session` 参数。若前端/后端回归或存储被篡改后返回 `../../x` 之类值，`join` 会解析到 `.jharness/sessions` 之外，读到工作区其他文件内容；`--session` 也会把非预期 id 传给 CLI，破坏“文件系统边界”。建议读取后立即按白名单字符集（如 `/^[A-Za-z0-9_-]+$/`）断言。
- 判断：id来自本测试自有受信服务和已验证创建流程，不是对外用户输入；生产API/CLI另校验Session ID。假想改坏服务使测试读错本地夹具路径不构成生产文件边界缺陷，未证明当前id可由模型控制。
- 证据：frontend/tests/turns.mjs:119

## OCR-1971 · 不采纳

- 位置：frontend/tests/turns.mjs:87；OCR原严重度：medium
- 计划：无
- 原意见：此时输入框已被核对流程清空（第 89 行的断言即为空串），`page.reload()` 大概率不会触发 beforeunload 对话框，于是这个一次性的 `dialog` 监听器会遗留到后续步骤，并在之后任意一个 dialog（例如移动端阶段点击“丢弃草稿”的确认框、或其他导航守卫）出现时自动 accept，静默改变用例语义；同时“刷新确实触发了未发送草稿保护”这一前提也从未被断言。建议改成显式计数：注册 `page.on('dialog')` 并在 reload 后断言对话框出现次数符合预期，或在不期望弹框时清理监听器。
- 判断：reload发生在点击核对消息受理之前，未决third输入此时仍保留；原意见把后面的清空断言提前套用。一次dialog接受是本次带未决草稿刷新所需，未证明监听实际遗留并放行其它对话框。
- 证据：frontend/tests/turns.mjs:87

## OCR-1972 · 部分采纳

- 位置：frontend/tests/turns.mjs:76；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：该断言的名称是“已完成会话仍保留后续输入框”，但实际只断言了第一个 heading 可见，与后续消息编辑器毫无关系：若续写输入框回归消失，此检查仍会 PASS，属于无效断言。建议断言真正的目标元素（如 `getByLabel('后续消息', { exact: true })` 的可见/可编辑状态）。
- 判断：此行名称说continuation composer但实际只看heading；采纳直接检查后续消息编辑器。后续fill/send已要求编辑器存在，不能把这一弱行推论成整个多轮套件完全未覆盖编辑器。
- 证据：frontend/tests/turns.mjs:76

## OCR-1973 · 部分采纳

- 位置：scripts/jharness-cancel.ps1:8；OCR原严重度：medium
- 计划：R6-03 / P3
- 原意见：JAVA_HOME 只做了非空校验，既没有验证 `bin/java.exe` 是否存在，也没有验证运行时是否为错误信息所要求的 JDK 21。触发条件：JAVA_HOME 指向 JRE、指向已删除的目录、或以 Windows 常见方式被写成 `"C:\..."`（含引号）。此时脚本会一直走到第 13 行才以 CommandNotFoundException/“找不到路径”失败，报错信息与“Set JAVA_HOME to JDK 21”提示无关，无法定位根因；若指向的 JDK 版本过低（< 21），则可能在错误的 JVM 上执行取消逻辑。建议在此处直接校验可执行文件存在，并在发现非 21 版本时给出明确错误。
- 判断：cancel脚本仅查JAVA_HOME非空，缺java可执行/版本前置；可补明确诊断但不应对错误JDK静默回退到另一安装。
- 证据：scripts/jharness-cancel.ps1:8

## OCR-1974 · 不采纳

- 位置：scripts/jharness-cancel.ps1:7；OCR原严重度：medium
- 计划：无
- 原意见：相对路径的 `-StateDirectory` 会按调用者当前工作目录解析（Java 侧 `Path.of(args[1])` 不做绝对化，见 CancelMain:14），而不是按 `$workspacePath` 解析。触发条件：`cd` 到其他目录或用 `pwsh -File scripts/jharness-cancel.ps1 -RunId <id> -StateDirectory .jharness/runs` 调用时，路径会指向另一个位置的状态文件，可能取消掉错误的运行记录，或落在预期的 `.jharness/runs` 树之外；同时若目录不存在，Java 侧只会返回 NOT_FOUND（退出码 2），用户无法区分“运行不存在”和“状态目录写错”。建议将相对路径锚定到工作区根，并在调用前确认目录存在。
- 判断：显式-StateDirectory相对路径按调用者cwd是常规CLI路径语义，默认值才按仓库根；未约定所有参数以workspace根解析，不能擅自改变。
- 证据：scripts/jharness-cancel.ps1:7

## OCR-1975 · 不采纳

- 位置：scripts/jharness-cancel.ps1:13；OCR原严重度：medium
- 计划：无
- 原意见：`java.exe` 被硬编码，脚本在 Linux/macOS 的 PowerShell 7 下无法工作（JDK 的启动器无 `.exe` 后缀；仓库已在 docs/testing/M9-hosted-linux-manifest.json 与 M9-hosted-macos-manifest.json 中收录该脚本）。触发条件：在非 Windows 主机执行 `pwsh -File scripts/jharness-cancel.ps1 -RunId <id>`，`& "$JAVA_HOME/bin/java.exe"` 会因不存在而抛错，取消能力在这些平台上不可用。建议按 `$IsWindows`/`$PSVersionTable` 选择 `java` 或 `java.exe`（并复用上面已校验的 `$javaExe`）。
- 判断：jharness-cancel.ps1是Windows辅助入口，POSIX有Java CLI/取消服务；被源码manifest收录不等于承诺pwsh跨平台。exe后缀未违当前支持范围。
- 证据：scripts/jharness-cancel.ps1:13

## OCR-1976 · 不采纳

- 位置：scripts/jharness-cancel.ps1:11；OCR原严重度：low
- 计划：无
- 原意见：jar 存在性检查（Test-Path）与第 13 行的 JVM 启动之间存在 TOCTOU 窗口：若并发执行 `mvnw clean package` 或清理 target，jar 可能在检查后被删除/替换，此时 JVM 以 NoClassDefFoundError 启动失败并返回 JVM 的通用退出码，而不是 CancelResult 的 0/2/3/4/5 之一（见 LocalRunControl.CancelResult），调用方会把“构建产物消失”误判为其他取消结果；另外工件的 `0.1.0-SNAPSHOT` 文件名在此写死，与 pom 版本重复维护，版本一旦变更即使已构建也会命中“Build first”提示。建议在失败时显式提示检查构建产物，或从 target 目录按模式解析实际 jar 名。
- 判断：构建检查后并发clean是外部破坏运行前置，JVM明确失败不会伪造取消事实；仓库脚本固定当前0.1.0版本是合法布局，不用模糊glob误选旧jar。
- 证据：scripts/jharness-cancel.ps1:11

## OCR-1977 · 部分采纳

- 位置：scripts/collect-test-evidence.ps1:12；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：证据来源未与本次执行绑定（第 11-14 行）：脚本只检查日志文本中出现过 `[INFO] BUILD SUCCESS`，既不校验日志中实际执行的 Maven 命令、也不校验日志的生成时间/退出码/`-Slice` 归属。`-VerificationCommand` 是自由文本，会被原样写入 `command` 字段（第 70 行附近），因此传入一份旧的成功日志（例如 `mvn test`、`-DskipTests`、只跑了单个测试类，或另一个 Milestone 的日志）即可生成“命令与实际验证不符”的证据。另外 `$logPath` 不存在时 `Select-String` 会先抛通用错误，第 13 行的明确提示反而不可达。建议先用 `Test-Path -LiteralPath $logPath` 判空，并校验日志头部/命令行与 `$VerificationCommand` 一致、日志时间不早于本次采集开始时间。
- 判断：PowerShell采集器为历史阶段通用记录工具，日志成功/单向mtime/固定Maven版本与部分源名单不能独立证明本次完整验收；采纳新run清单、完整case计数/失败/跳过校验、实际工具版本与输入摘要绑定。保留历史原证据，具体原意见字段纳入共享采集器负例。
- 证据：scripts/collect-test-evidence.ps1:12

## OCR-1978 · 部分采纳

- 位置：scripts/collect-test-evidence.ps1:25；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：时间戳校验是单向的（第 25 行）：只禁止“报告比日志新”，却允许“报告比日志旧”。默认命令含 `clean` 时目录会被清空，但 `-VerificationCommand` 可由调用方任意指定（例如 `verify` 或 `test`，且 M8 分片场景使用不同的日志名）；此时上一轮失败运行残留的 surefire 报告（比日志旧）会与一份新的 BUILD SUCCESS 日志拼接，被当作本次运行的测试结果写入证据。建议要求 `$report.LastWriteTimeUtc` 落在本次验证的时间窗内（例如不早于日志首次写入时间，或与 `pom.xml` 的构建产物时间交叉校验），并显式记录报告目录的最后清理/生成时间。
- 判断：PowerShell采集器为历史阶段通用记录工具，日志成功/单向mtime/固定Maven版本与部分源名单不能独立证明本次完整验收；采纳新run清单、完整case计数/失败/跳过校验、实际工具版本与输入摘要绑定。保留历史原证据，具体原意见字段纳入共享采集器负例。
- 证据：scripts/collect-test-evidence.ps1:25

## OCR-1979 · 部分采纳

- 位置：scripts/collect-test-evidence.ps1:22；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：报告缺失被静默接受（第 22-23 行）：模块没有 `target/surefire-reports` 或目录中没有任何 `TEST-*.xml` 时 `$reports` 为空，脚本不报错也不记录该模块，最终 `totals` 可能全为 0，却仍输出 `buildResult = 'SUCCESS'` 的证据（例如测试被 profile 跳过、模块未参与构建、或 `clean` 后报告目录被移走）。建议：模块无报告时显式失败或写入 `reportsMissing` 标记，并在输出前断言至少存在一个 suite；同时确认是否还应覆盖 failsafe/集成测试报告目录（当前仅扫描 surefire，`verify` 阶段的 IT 结果不会进入 totals）。
- 判断：PowerShell采集器为历史阶段通用记录工具，日志成功/单向mtime/固定Maven版本与部分源名单不能独立证明本次完整验收；采纳新run清单、完整case计数/失败/跳过校验、实际工具版本与输入摘要绑定。保留历史原证据，具体原意见字段纳入共享采集器负例。
- 证据：scripts/collect-test-evidence.ps1:22

## OCR-1980 · 部分采纳

- 位置：scripts/collect-test-evidence.ps1:60；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：`buildResult = 'SUCCESS'` 是硬编码常量（第 68 行附近），且 totals 汇总时未校验 `failures`/`errors` 是否为 0。只要日志文本含 BUILD SUCCESS、报告时间早于日志，包含 `failures > 0` 的报告也会被完整写入证据并标注 SUCCESS，形成自相矛盾的证据文件。建议在生成 `$evidence` 前断言 `$totals.failures -eq 0 -and $totals.errors -eq 0`（否则抛错），并让 `buildResult` 由实际统计与日志检查共同推导，而不是常量。
- 判断：PowerShell采集器为历史阶段通用记录工具，日志成功/单向mtime/固定Maven版本与部分源名单不能独立证明本次完整验收；采纳新run清单、完整case计数/失败/跳过校验、实际工具版本与输入摘要绑定。保留历史原证据，具体原意见字段纳入共享采集器负例。
- 证据：scripts/collect-test-evidence.ps1:60

## OCR-1981 · 部分采纳

- 位置：scripts/collect-test-evidence.ps1:46；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：TOCTOU 竞态（第 25、45-47 行）：`LastWriteTimeUtc` 来自 `Get-ChildItem` 枚举时的缓存值，随后才 `Get-Content`/`Get-FileHash` 读取内容；并发写入（IDE 保存、`spotless:apply`、后台构建）可在检查通过后改变文件内容，导致证据里的 SHA256/测试结果并非已被验证的产物。建议先读取文件字节、对同一份字节计算哈希，读取后再 `Get-Item` 复核 `LastWriteTimeUtc` 与大小未变化，不一致则抛错；报告 XML 亦同（先读再复核 mtime）。
- 判断：PowerShell采集器为历史阶段通用记录工具，日志成功/单向mtime/固定Maven版本与部分源名单不能独立证明本次完整验收；采纳新run清单、完整case计数/失败/跳过校验、实际工具版本与输入摘要绑定。保留历史原证据，具体原意见字段纳入共享采集器负例。
- 证据：scripts/collect-test-evidence.ps1:46

## OCR-1982 · 部分采纳

- 位置：scripts/collect-test-evidence.ps1:71；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：`mavenVersion = '3.9.9'` 为硬编码（与 `.mvn/wrapper/maven-wrapper.properties` 的 `distributionUrl` 当前值一致，但无关联）。一旦 wrapper 升级（或调用方用 `VerificationCommand` 指定系统 `mvn`），证据会错误描述验证环境。建议从 `maven-wrapper.properties` 的 `distributionUrl` 解析版本，或在采集时执行 `& $mvnw -v` 读取 `Apache Maven x.y.z`。
- 判断：PowerShell采集器为历史阶段通用记录工具，日志成功/单向mtime/固定Maven版本与部分源名单不能独立证明本次完整验收；采纳新run清单、完整case计数/失败/跳过校验、实际工具版本与输入摘要绑定。保留历史原证据，具体原意见字段纳入共享采集器负例。
- 证据：scripts/collect-test-evidence.ps1:71

## OCR-1983 · 部分采纳

- 位置：scripts/collect-test-evidence.ps1:27；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：`javaVersion` 可能为 null 且无校验（第 28-30 行）：surefire XML 的 `<properties>` 未包含 `java.version`（某些 surefire/配置下不写 properties）时 `$javaVersion` 保持 `$null`，证据中 `javaVersion: null` 仍被视为有效；且只取第一个 suite 的值，不校验各模块 JVM 是否一致。建议在生成证据前断言 `$javaVersion` 非空，并对所有 suite 的 `java.version` 做一致性校验（不一致时报错或列入 `javaVersions`）。
- 判断：PowerShell采集器为历史阶段通用记录工具，日志成功/单向mtime/固定Maven版本与部分源名单不能独立证明本次完整验收；采纳新run清单、完整case计数/失败/跳过校验、实际工具版本与输入摘要绑定。保留历史原证据，具体原意见字段纳入共享采集器负例。
- 证据：scripts/collect-test-evidence.ps1:27

## OCR-1984 · 部分采纳

- 位置：scripts/collect-test-evidence.ps1:50；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：源码哈希采用硬编码白名单（第 50 行，M8 额外名单在第 55-58 行），存在两个问题：1) 新引入但参与构建的输入（如 `.mvn/maven.config`、`toolchains.xml`、spotless/checkstyle 配置、wrapper 的 `.jar`、其他脚本）不会被纳入 `sourceSha256`，无法证明该快照就是被验证的输入；2) 名单中任一路径在当前分支不存在（例如某 Milestone 的示例文件被删除/重命名）时，`Get-FileHash` 在 `$ErrorActionPreference='Stop'` 下会直接中断整个采集，而错误信息不指出是哪一项缺失。建议改为“按规则枚举 + 白名单”并在哈希前显式校验存在性、缺失时给出明确错误；M8 名单也应合并进同一数据结构，避免两份列表漂移。
- 判断：PowerShell采集器为历史阶段通用记录工具，日志成功/单向mtime/固定Maven版本与部分源名单不能独立证明本次完整验收；采纳新run清单、完整case计数/失败/跳过校验、实际工具版本与输入摘要绑定。保留历史原证据，具体原意见字段纳入共享采集器负例。
- 证据：scripts/collect-test-evidence.ps1:50

## OCR-1985 · 部分采纳

- 位置：scripts/collect-test-evidence.ps1:8；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：兼容性：脚本使用了仅 .NET Core 提供的 `[IO.Path]::GetRelativePath`，且 `Set-Content -Encoding utf8` 在 Windows PowerShell 5.1 下会写入 BOM（PS7 不写），同一命令在 5.1 与 7.x 下行为/产物字节不一致（BOM 会改变结果文件哈希、并可能破坏下游严格 JSON 解析）。脚本中没有声明运行时要求；`docs/testing/M0~M2` 的调用示例是 `.\scripts\collect-test-evidence.ps1` 而非 `pwsh -File`。建议在文件头加 `#Requires -Version 7.0`，或改用 `Join-Path -Resolve`/`Resolve-Path -Relative` 与显式无 BOM 写出（`[IO.File]::WriteAllText` + UTF8Encoding($false)）。
- 判断：collect-test-evidence用GetRelativePath等.NET Core API且无Requires，PS5.1会失败/UTF8带BOM。采纳明确pwsh7前置，不反向把原PS7记录视作有BOM。
- 证据：scripts/collect-test-evidence.ps1:8

## OCR-1986 · 采纳

- 位置：scripts/open-workbench.ps1:126；OCR原严重度：high
- 计划：R6-03 / P2
- 原意见：Test-Ready produces false negatives in two ways. (1) The WEB line is printed only once; if the first probe fails, `$address` is set to $null and never retried, so the loop spins until the 30s deadline and reports 'Workbench did not become ready' even though the JVM is already listening (or the first GET exceeded the 1.5s timeout). Keep retrying Test-Ready at an interval until it succeeds or the deadline is reached. (2) When the response has no Content-Type header, `$null -notlike 'text/html*'` evaluates to $true, so a fully ready page is rejected; only return $false when ContentType is present and does not match. Fix both, otherwise the single-probe issue alone guarantees a timeout.
- 判断：open-workbench解析一次WEB后首次Test-Ready失败便清address，WEB不再输出导致不重试；应保留候选origin并在截止前轮询。Content-Type缺失能否视就绪需保持严格资源校验，不宜无条件放宽。
- 证据：scripts/open-workbench.ps1:126

## OCR-1987 · 部分采纳

- 位置：scripts/open-workbench.ps1:116；OCR原严重度：high
- 计划：R6-03 / P2
- 原意见：stderr 用 `ReadToEndAsync()` 在整个会话期间无界累积到内存（`$errors` 从未使用），同时第 129 行之后 stdout 不再被消费（`$line` 停在最后一次读取上）。子进程再向 stdout 写日志时，管道缓冲区写满会阻塞宿主线程，Web 服务和 `:quit` 处理都可能卡住；长时间运行时 stderr 还会持续吃内存。建议就绪后继续在后台消费 stdout（有界丢弃或按行读取），并对 stderr 设定保留上限。
- 判断：就绪后stdout停止消费、stderr ReadToEndAsync无界留存属实；当前serve通常安静，但长期诊断输出可阻塞/增长。采纳持续有界丢弃或尾日志读取。
- 证据：scripts/open-workbench.ps1:116

## OCR-1988 · 部分采纳

- 位置：scripts/open-workbench.ps1:0；OCR原严重度：high
- 计划：R6-03 / P2
- 原意见：tokenEnv 只做了变量名格式与短黑名单校验，而工作区的 `.jharness/config.json` 属于不可信输入：它可以把 tokenEnv 指向 USERPROFILE/APPDATA/LOCALAPPDATA/PSModulePath/SSH_AUTH_SOCK 等已存在的宿主变量，第 111 行随后在子进程环境里覆盖其值，破坏 JVM 对 home/临时目录的解析；若该变量恰好存着 32 字符以上的其它凭据，第 92 行还会把它直接当作本机 Web 的 Bearer token，等同于把无关密钥变成鉴权口令。建议固定使用 JHARNESS_WEB_TOKEN，或改为显式白名单/前缀校验后才允许写入子进程环境。
- 判断：tokenEnv允许自定义且黑名单漏USERPROFILE等，生成token会覆盖该子环境槽；用户显式config是受信启动输入，非无鉴权注入。采纳限制危险宿主槽或固定专用token槽，同时兼容已有合法配置迁移。
- 证据：scripts/open-workbench.ps1:1

## OCR-1989 · 部分采纳

- 位置：scripts/open-workbench.ps1:40；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：候选探测里的 Start()/Kill()/Result 都可能抛异常（exe 不可执行/被杀进程已退出/流读取失败），异常会直接从 Resolve-Java 冒出，后面的候选（例如 PATH 上的 JDK 21）不会被尝试，最终只报 'Java 21 was not found'。应在循环内 try/catch 后 continue。另外 WaitForExit(5000) 返回时异步 stderr 读取可能尚未完成，需再等待该 Task（或调用无参 WaitForExit），否则可能把 21 误判为非 21；同时 RedirectStandardOutput 已开启却从不读取，候选若向 stdout 输出较多内容会先写满缓冲再被 5s 超时 Kill。
- 判断：Resolve-Java候选probe某些Start/Kill/Task异常可提前退出整个候选枚举，采纳逐候选捕获和有界双流读取；不能无条件无限WaitForExit。
- 证据：scripts/open-workbench.ps1:41

## OCR-1990 · 部分采纳

- 位置：scripts/open-workbench.ps1:140；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：`[Console]::ReadLine()` 是阻塞调用：Java 宿进程崩溃时循环不会醒来，用户必须敲一次回车才会进入 finally 并清理；而当 stdin 被重定向（`powershell -File ... < nul`、CI/计划任务调用）时 ReadLine 立即返回 null，脚本会在打印 WORKBENCH_READY 后马上关闭刚启动的工作台。建议改为 ReadLineAsync 与 HasExited 竞争，或在等待输入期间轮询宿主状态。
- 判断：Console.ReadLine阻塞会延迟检测host退出；重定向EOF关闭宿主是保守有意生命周期，不能让无控制台后台永驻。采纳输入与host退出竞争，保留EOF安全关闭。
- 证据：scripts/open-workbench.ps1:140

## OCR-1991 · 采纳

- 位置：scripts/open-workbench.ps1:156；OCR原严重度：medium
- 计划：R6-03 / P2
- 原意见：宿主进程的退出码从未传播给调用方：正常 `:quit` 与 Java 以非 0 退出（配置错误、端口占用等）走到这里都保持 $exitCode=0，最后的 `exit $exitCode` 仍返回成功，自动化无法发现失败。另外 HasExited 检查与 WriteLine 之间存在竞态，进程恰好在此刻退出时写入会抛 IOException，被 catch 后把 exitCode 置 1 并打印 'cleanup was not confirmed'，与真实原因不符。建议在 Dispose 前读取 `$hostProcess.ExitCode` 并入返回码，并对写 stdin 的 IOException 单独忽略。
- 判断：host自然非零退出未并入exitCode，launcher可能返0；应回收前传播自有host退出失败，处理stdin关闭竞态并保持脱敏诊断。
- 证据：scripts/open-workbench.ps1:156

## OCR-1992 · 不采纳

- 位置：scripts/open-workbench.ps1:7；OCR原严重度：low
- 计划：无
- 原意见：`-NoClipboard` 被声明但脚本里从未使用（当前实现根本不做剪贴板操作），调用方（含 scripts/test-open-workbench.py 传参）会误以为该开关有实际作用。建议要么实现对应行为，要么删除该参数并在文档中说明不再复制凭据。
- 判断：NoClipboard是兼容保留参数，启动器已不触剪贴板，安全效果已经满足。删除会破坏现有test/脚本调用，不为死参数认产品bug。
- 证据：scripts/open-workbench.ps1:7

## OCR-1993 · 部分采纳

- 位置：scripts/aggregate-m1059-ci.py:22；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：摘要是在校验完成后重新调用 `p.read_bytes()` 读取的，与第 10 行 `json.loads(path.read_bytes())` 是两次独立读取：若报告在两次读取之间被重写（CI 并发写、符号链接指向可变文件、`rglob` 命中的是会被替换的文件），记录进 PASS 的 sha256 与真正被校验的字节不一致，而文件也可能此时已被删除导致 `FileNotFoundError`。建议第一次读取时保留 bytes，用同一份 bytes 既做解析又做 sha256。
- 判断：aggregate解析与返回摘要两次read，竞态可绑定非同字节；采纳单次bytes解析+摘要，低成本加强证据一致性，非已发生篡改。
- 证据：scripts/aggregate-m1059-ci.py:22

## OCR-1994 · 部分采纳

- 位置：scripts/aggregate-m1059-ci.py:24；OCR原严重度：low
- 计划：R0-01 / P3
- 原意见：入口直接取 `sys.argv[1]`，未校验参数个数；不带参数运行时会抛 `IndexError` 而非可读的使用提示。建议在 `aggregate` 调用前检查 `len(sys.argv) == 2`，否则输出用法并以非零码退出。
- 判断：aggregate缺参IndexError/缺字段KeyError会非零fail-closed，不会误PASS；采纳argparse与报告schema错误定位提升诊断。
- 证据：scripts/aggregate-m1059-ci.py:24

## OCR-1995 · 部分采纳

- 位置：scripts/aggregate-m1059-ci.py:14；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：`r['runnerOs']` 直接下标访问：报告被截断或字段缺失时会抛 `KeyError`，同一函数内 `r['java']['failures']`、`r['suites']`/`s['cases']`、`r[field]` 等同理，最终以 traceback 而非明确的 `ValueError` 失败，校验脚本无法区分“报告格式非法”和“校验不通过”。建议用 `r.get(...)` 并显式校验字段存在性与类型（同时校验 `json.loads` 结果为 dict）。
- 判断：aggregate缺参IndexError/缺字段KeyError会非零fail-closed，不会误PASS；采纳argparse与报告schema错误定位提升诊断。
- 证据：scripts/aggregate-m1059-ci.py:14

## OCR-1996 · 不采纳

- 位置：scripts/aggregate-m1059-ci.py:18；OCR原严重度：high
- 计划：无
- 原意见：`sorted((s['name'], c) ...)` 的元组第二元素是用例的 dict 对象。Python 3 中 dict 不支持 `<` 比较，因此只要报告里存在两个同名 suite（`s['name']` 相同），排序时就会退化为比较 dict 并抛出 `TypeError: '<' not supported between instances of 'dict' and 'dict'`，而不是预期的 `ValueError('Different Java cases')`，导致合法报告直接把脚本打崩。建议只对可比较的键排序（如 `(s['name'], c['name'])`）并对用例键做正规化。
- 判断：collector写suites.cases为testcase名称字符串，aggregate排序(suiteName,c)比较字符串合法；意见误把c当dict，与实际生产者schema相反。
- 证据：scripts/aggregate-m1059-ci.py:18

## OCR-1997 · 部分采纳

- 位置：scripts/aggregate-m1059-ci.py:12；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：`len(reports)!=3` 只校验数量，不校验三份报告是否为独立来源。`runnerOs` 来自报告正文本身，把同一份 `runner.json` 复制三份并分别改写 `runnerOs` 为 Windows/Linux/macOS，即可绕过 OS 集合校验并让 `runnerReports` 里三份文件哈希完全一致却仍判定 PASS，使“三个真实 CI runner”的保证失效。建议显式校验三份报告的 `ciRunId`/文件内容摘要互不相同（同一 ciRunId 只能出现一次），或额外要求每份报告携带可独立验证的 runner 标识。
- 判断：JSON自报runnerOs无法独立证明三台真实runner，应绑定下载artifact/job来源；三平台本来同ciRunId，建议要求runId互异错误，改runnerOs后hash也不会相同。仅采纳真实job/artifact来源校验。
- 证据：scripts/aggregate-m1059-ci.py:12

## OCR-1998 · 采纳

- 位置：scripts/aggregate-m1059-ci.py:16；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：该循环只比较三份报告之间是否“相同”，未校验取值本身非空。若 `commit`/`sourceSha256`/`uiSha256` 在三份报告中都是 `null` 或空串，相等性检查会通过，随后 `return` 里把空 `commit` 写进 PASS 记录，等于放行了没有真实源码/UI 指纹的报告。`ciRunId` 已在上一行被 `not ...` 校验，其余三个字段也应校验非空（以及 sha256 的 64 位十六进制格式）。
- 判断：aggregate只比较commit/sourceSha256/uiSha256相等，三份空值也可能通过；应先校验非空集合与哈希格式、预期键完整性。
- 证据：scripts/aggregate-m1059-ci.py:16

## OCR-1999 · 部分采纳

- 位置：scripts/collect-m1051-evidence.py:56；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：第56行（约）的通过判定过于宽松：`isinstance(c, str)` 会把任意字符串型 check 直接视为通过，因此验收记录中若以字符串形式记录失败项（例如 `"fail: ..."`），闸门仍会静默放行；同时若 `c` 是数字/列表等既非 str 也非 dict 的条目，`c.get('passed')` 会抛 AttributeError，报错信息变成栈回溯而不是可读的 'Failed acceptance'。建议只接受显式的通过标记，字符串型条目需按其内容判定成功而非默认成功。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m1051-evidence.py:56

## OCR-2000 · 部分采纳

- 位置：scripts/collect-m1051-evidence.py:64；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：第62、64、71行把证据 JSON 中的路径直接拼到 ROOT 上再做哈希，未做规范化与边界校验：`image['path']`、`sourceSha256` 的 key、`item['destination']` 若含 `../` 或绝对路径，`ROOT / ...` 会解析到仓库外（例如 `../../etc/...`、`C:/...`），从而对树外文件取哈希并通过 `sha()` 的返回值写入证据，校验的可信边界被突破；另外目标文件不存在时抛的是 FileNotFoundError 而非带语义的 ValueError。建议先 `resolve()` 再用 `is_relative_to(ROOT.resolve())` 做包含性校验，并统一转为可读的校验失败。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：第62、64、71行把证据 JSON 中的路径直接拼到 ROOT 上再做哈希，未做规范化与边界校验：`image['path']`、`sourceSha256` 的 key、`item['destination']` 若含 `..
- 证据：scripts/collect-m1051-evidence.py:64

## OCR-2001 · 部分采纳

- 位置：scripts/collect-m1051-evidence.py:78；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：第78行有两个问题：(1) 当 `ui-files.txt` 中没有 `.js` 条目时，`next(p for p in files if p.endswith('.js'))` 会抛 StopIteration（且未加默认值，异常类型与其它校验失败的 ValueError 不一致）；(2) 只检查了第一个 JS 分块，若打包产物被拆成多个 chunk，关键文案 `为什么结果待核实` 落在后续 chunk 时校验会漏检。建议遍历所有 `.js` 条目做检查，并在集合为空时用显式 ValueError 报错。
- 判断：该collector确用next选择首个.js检查新UI文案，当前单bundle可用，拆chunk后可误拒，缺js裸StopIteration也不清楚。采纳非空JS集合及所有受管chunk语义检查，不将未来分包当当前功能缺失。
- 证据：scripts/collect-m1051-evidence.py:78

## OCR-2002 · 部分采纳

- 位置：scripts/collect-m1051-evidence.py:37；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：第37、44行只用 `st_mtime <= log.stat().st_mtime` 判定新鲜度：文件系统时间戳粒度（FAT/网络盘 2s）、时钟回拨、或 checkout/复制重置 mtime 都会让陈旧报告或源码看起来“未过期”，反之也会误报。更关键的是 verify 日志本身没有与当前 JAR/产物建立内容关联——一个旧的 `M10.5.1-verify.log` 仍能通过 `'[INFO] BUILD SUCCESS'` 判定，导致证据可能来自非当前源码的构建。建议除 mtime 外，补充内容层面的关联（例如日志中记录构建产物的哈希/时间戳，或把 report sha256 与日志内的汇总值比对）。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：第37、44行只用 `st_mtime <= log.stat().st_mtime` 判定新鲜度：文件系统时间戳粒度（FAT/网络盘 2s）、时钟回拨、或 checkout/复制重置 mtime 都会让陈旧报告或源码看起来“未过
- 证据：scripts/collect-m1051-evidence.py:37

## OCR-2003 · 部分采纳

- 位置：scripts/archive-m1059-local.py:35；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：归档过程非原子且无回滚：各证据文件直接就地写入 docs/testing，manifest 最后才写，中途任一步失败（如 target/release-evidence.json 缺失）会留下既未被 records 登记、又与旧 manifest 不匹配的残留证据，使已发布的 docs/testing 处于半更新状态；`write_text` 也非原子，中断可能截断已有的 M10.5.9-results.json。建议先把本次所有文件写到临时目录并在全部成功后整体 `os.replace`，或在异常路径清理本次已写入的目标文件，再写 manifest。
- 判断：archive-m1059-local逐份write、校验和copy重读、元数据路径/输出名未经根和枚举约束、非浏览器记录不回比runner摘要均为实际缺口。采纳同字节验证/边界/完整清单后staging原子归档，保留已有历史事实与失败记录。
- 证据：scripts/archive-m1059-local.py:35

## OCR-2004 · 部分采纳

- 位置：scripts/archive-m1059-local.py:18；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：校验与复制不是同一份字节：调用方先 `sha(source)` 校验、`copy()` 再 `source.read_bytes()` 重读一遍，两次读取之间文件被改（TOCTOU）不会被发现；且 `records[name]['sha256']` 是对目标文件重算的摘要，从未回比 `record['sha256']`，因此归档字节即使与已验证证据不一致也不会报错。建议只读一次 bytes，用同一份 bytes 完成校验、写入与摘要计算，并断言目标摘要等于 source 摘要；另外 `source.relative_to(ROOT)` 目前在写盘之后执行，应先做包含性校验再写文件。
- 判断：archive-m1059-local逐份write、校验和copy重读、元数据路径/输出名未经根和枚举约束、非浏览器记录不回比runner摘要均为实际缺口。采纳同字节验证/边界/完整清单后staging原子归档，保留已有历史事实与失败记录。
- 证据：scripts/archive-m1059-local.py:18

## OCR-2005 · 部分采纳

- 位置：scripts/archive-m1059-local.py:20；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：文件系统边界未校验：`record['path']` 直接与 ROOT 拼接，Path 语义下绝对路径会整体替换 ROOT，`../..` 可穿越到仓库外读取任意文件；`record['engine']`/`record['fixture']` 也未做白名单校验就拼进目标文件名，含 `/`、`..` 时可写出 docs/testing 之外。更严重的是越界只在 `copy()` 内的 `source.relative_to(ROOT)` 才被发现，而该调用发生在 `target.write_bytes()` 之后，仓库外文件内容会先被复制进 docs/testing 并残留（`records` 不会登记，后续也不会被清理）。建议先 `resolve()` 并校验 `source.is_relative_to(ROOT)`、`target.is_relative_to(DEST)`，同时约束 engine ∈ {chromium, firefox, webkit}、fixture 仅允许 `[A-Za-z0-9._-]`，且在写盘前完成全部校验。
- 判断：archive-m1059-local逐份write、校验和copy重读、元数据路径/输出名未经根和枚举约束、非浏览器记录不回比runner摘要均为实际缺口。采纳同字节验证/边界/完整清单后staging原子归档，保留已有历史事实与失败记录。
- 证据：scripts/archive-m1059-local.py:20

## OCR-2006 · 部分采纳

- 位置：scripts/archive-m1059-local.py:23；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：非浏览器证据无任何完整性校验：`sdk/examples/native` 以及下方 target/*.json 都是无条件复制，collect 与 archive 之间文件被替换或损坏也不会被发现，未经验证的字节会被直接发布到 docs/testing，与脚本「归档已验证证据」的语义不符。建议在 runner.json 中为这些产物记录 sha256，并在此处逐个比对后再复制。
- 判断：archive-m1059-local逐份write、校验和copy重读、元数据路径/输出名未经根和枚举约束、非浏览器记录不回比runner摘要均为实际缺口。采纳同字节验证/边界/完整清单后staging原子归档，保留已有历史事实与失败记录。
- 证据：scripts/archive-m1059-local.py:23

## OCR-2007 · 部分采纳

- 位置：scripts/collect-m10-settings-evidence.py:56；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：验收结果校验过宽：任何一个字符串都会被当作“通过”（空字符串、"FAILED: xxx" 同样通过），字典则只做 `c.get('passed')` 的真值判断（字符串 "false" 也为真）。同时这里只校验 `len(checks)` 等于硬编码数量，不校验 check 的身份/唯一性，因此一份内容完全不同但数量相同的 checks 数组也能通过门禁，使 PASS 证据可能是伪造/漂移的。建议只接受显式布尔状态，并校验 check 名称集合与顺序。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m10-settings-evidence.py:56

## OCR-2008 · 部分采纳

- 位置：scripts/collect-m10-settings-evidence.py:61；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：来自缓存证据 JSON 的路径未做仓库边界校验：`ROOT / path` 在 path 为绝对路径或含 `..` 时会逃逸仓库（例如 `../../some/secret` 或 `C:/...`），从而对仓库外文件做哈希并写入证据，既破坏“证据只覆盖仓库输入”的语义，也可被构造的 acceptance JSON 利用来读取/泄露仓库外文件摘要。`upstream['files']` 的 `item['destination']`（以及 JAR 内 `ui-files.txt` 的条目）同理。建议 `resolve()` 后用 `is_relative_to(ROOT)` 断言再计算哈希，并拒绝绝对路径。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：来自缓存证据 JSON 的路径未做仓库边界校验：`ROOT / path` 在 path 为绝对路径或含 `..` 时会逃逸仓库（例如 `../../some/secret` 或 `C:/...`），从而对仓库外文件做哈希并写入证
- 证据：scripts/collect-m10-settings-evidence.py:61

## OCR-2009 · 部分采纳

- 位置：scripts/collect-m10-settings-evidence.py:75；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：`next(...)` 在 `ui-files.txt` 没有任何 `.js` 条目时会抛 `StopIteration`，报错信息与真实原因（包内缺少 JS）无关，难以定位；并且只检查了第一个 JS 文件，若 UI 被拆成多个 chunk 且标记文本落在第二个及之后的 chunk 中，会误判为 “Settings missing from bundle”。建议先校验存在 JS 条目（`require`），再把所有 `.js` 内容拼接后做标记检查。
- 判断：该collector确用next选择首个.js检查新UI文案，当前单bundle可用，拆chunk后可误拒，缺js裸StopIteration也不清楚。采纳非空JS集合及所有受管chunk语义检查，不将未来分包当当前功能缺失。
- 证据：scripts/collect-m10-settings-evidence.py:75

## OCR-2010 · 采纳

- 位置：scripts/collect-m10-settings-evidence.py:47；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：客户端测试日志校验使用的是无锚点子串匹配：`pass\s+8\b` 可被 "bypass 8"、日志中的其它数字统计等无关文本命中，`fail 0`/`skipped 0` 也可能来自不同行（甚至不同测试块），只要整份日志任意位置出现这些片段即判为通过，无法证明真实汇总结果是 8 pass / 0 fail / 0 skip。建议锚定到测试汇总行（如按行 `re.fullmatch(r'\s*pass\s+8\s*$', line, re.M)`）或直接解析 TAP 汇总，同时确认汇总只出现一次。
- 判断：该collector用re.search在整份客户端文本匹配分散pass/fail/skipped，未解析单轮完整摘要/退出码/构建身份；确可命中陈旧或拼接结果。改用结构化测试结果并绑定本次runId，不能只加一个mtime下界。对应主张：客户端测试日志校验使用的是无锚点子串匹配：`pass\s+8\b` 可被 "bypass 8"、日志中的其它数字统计等无关文本命中，`fail 0`/`skipped 0` 也可能来自不同行（甚至不同
- 证据：scripts/collect-m10-settings-evidence.py:47

## OCR-2011 · 部分采纳

- 位置：scripts/collect-m10-settings-evidence.py:79；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：`is_file()` 过滤会把“应当存在但实际不存在”的输入静默丢弃：显式列出的 `package-lock.json`、`tests/settings.mjs` 等若缺失，不会失败，只是少一条哈希，最终仍生成 status=PASS 的证据（`frontend/src` 目录缺失时 `rglob` 也只会返回空）。这使前端输入快照可能不完整却无任何提示。建议对显式清单单独 `require` 全部存在，`rglob` 结果再用 `is_file()` 排除目录。
- 判断：前端输入字典if is_file会静默丢显式缺失package-lock/fixture及不存在src目录；应区分必须输入与枚举出的常规文件，逐个缺失失败，并记录完整构建清单。
- 证据：scripts/collect-m10-settings-evidence.py:79

## OCR-2012 · 部分采纳

- 位置：scripts/collect-m10-auto-evidence.py:56；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：验收项校验过弱，无法排除未决/失败状态： 1) 任何非 None 字符串都会被当作通过，因此 `"UNKNOWN"`、`"SKIP"`、`"FAIL"` 等状态也能让整条 evidence 变成 PASS（违反“UNKNOWN 状态不得被重放”的要求）； 2) `{"passed": "false"}` 这种非布尔值同样为真值，直接放行； 3) 若 `checks` 实际是 dict（长度恰好等于 21/28/8 时上一行的 `len()` 校验会通过），迭代得到的是字符串 key，全部被无条件接受，整组检查形同虚设； 4) 若元素是 int/list 等对象，`c.get(...)` 会抛 AttributeError 而非带上下文的 ValueError。 建议显式约束 schema：必须是 list，元素必须是 dict，且 `passed is True`、`status` 只允许 `PASS`。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m10-auto-evidence.py:56

## OCR-2013 · 部分采纳

- 位置：scripts/collect-m10-auto-evidence.py:62；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：来自验收 JSON 的路径未经任何边界校验就与 `ROOT` 拼接（此处 `sourceSha256` 的 `path`、第 64 行 `image['path']`、第 70 行 `item['destination']`）：绝对路径（如 `C:/...`、`/etc/...`）会使 `ROOT / path` 直接丢弃 `ROOT`，`..` 序列也会逃出仓库，从而用仓库外的文件满足哈希比对，文件系统边界失效。另外 `data.get('sourceSha256', {})` 在字段缺失/为空时会让整段输入漂移校验静默跳过，也不要求该 map 覆盖 jar 构建的真实输入，抽查完整性无法保证。建议先 resolve 再校验 `is_relative_to(ROOT)`，并对必需键做存在性断言。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：来自验收 JSON 的路径未经任何边界校验就与 `ROOT` 拼接（此处 `sourceSha256` 的 `path`、第 64 行 `image['path']`、第 70 行 `item['destination']`）：绝
- 证据：scripts/collect-m10-auto-evidence.py:62

## OCR-2014 · 采纳

- 位置：scripts/collect-m10-auto-evidence.py:37；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：Surefire 报告只校验了 mtime 上界，缺少下界/构建关联，陈旧产物可被当作本次结果：如果未执行 `clean` 就重跑、或删除了测试类但 `target/surefire-reports` 残留旧报告，残留集合的总数仍可能凑满 314 而通过（第 43 行只比较总数）。同样问题更严重地存在于客户端测试：第 45-47 行对 `M10-auto-client.log` 完全没有新鲜度校验，任何一次历史运行留下的 `pass 9 / fail 0 / skipped 0` 日志都能满足正则，即使它与当前源码/JAR 无关。建议在 verify 前清理 `target`，或让日志/报告携带唯一 run id（或开始时间）并与本次 verify 绑定，同时给 client 日志加上与 sources 的下界比较。
- 判断：该collector用re.search在整份客户端文本匹配分散pass/fail/skipped，未解析单轮完整摘要/退出码/构建身份；确可命中陈旧或拼接结果。改用结构化测试结果并绑定本次runId，不能只加一个mtime下界。对应主张：Surefire 报告只校验了 mtime 上界，缺少下界/构建关联，陈旧产物可被当作本次结果：如果未执行 `clean` 就重跑、或删除了测试类但 `target/surefire-reports`
- 证据：scripts/collect-m10-auto-evidence.py:37

## OCR-2015 · 部分采纳

- 位置：scripts/collect-m10-auto-evidence.py:66；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：这里重新从磁盘读取 browser/launcher 结果（第 66-67 行），而进入 evidence 的 `acceptanceSha256` 是第 65 行 `records[name] = sha(file)` 早先算出的哈希：两次读取之间存在 TOCTOU 窗口，文件被替换/重写时，落盘的 `acceptanceSha256` 与实际用于 `paidRequests` 断言的 `runtime` 内容并非同一份数据，证据自洽性被破坏；同时也重复读盘。建议在循环内保留已解析的 `data`（例如 `parsed[name] = data`）直接复用，不要二次读取。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：这里重新从磁盘读取 browser/launcher 结果（第 66-67 行），而进入 evidence 的 `acceptanceSha256` 是第 65 行 `records[name] = sha(file)` 早先算出
- 证据：scripts/collect-m10-auto-evidence.py:66

## OCR-2022 · 部分采纳

- 位置：scripts/collect-m10-evidence.py:57；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：`item['destination']` 直接拼到 `frontend` 下，未做根目录约束：绝对路径或 `..` 段会逃出 frontend 目录，使该校验去比对仓库内外的任意文件；同时上面的 `len(...) == 21` 不排除重复 destination，实际被覆盖的源文件可能不足 21 个。建议对目标做 resolve + 包含性校验，并要求 destination 唯一。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：`item['destination']` 直接拼到 `frontend` 下，未做根目录约束：绝对路径或 `..` 段会逃出 frontend 目录，使该校验去比对仓库内外的任意文件；同时上面的 `len(...) == 21`
- 证据：scripts/collect-m10-evidence.py:57

## OCR-2023 · 采纳

- 位置：scripts/collect-m10-evidence.py:68；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：客户端测试证据只在整份日志上做三条松散正则匹配：既没有与当前 frontend 源码/`package-lock.json` 的时间或哈希绑定（陈旧日志、重放日志都能命中），也无法排除日志由多次运行拼接而成（旧运行的 `pass 8` 与另一次运行的 `fail 0` 同时命中）；`clientTests` 中的 8 也是硬编码，未与真实用例名比对。建议校验日志 mtime 不早于 lock/源码，并解析出具体用例名集合后与硬编码计数一起核对。
- 判断：该collector用re.search在整份客户端文本匹配分散pass/fail/skipped，未解析单轮完整摘要/退出码/构建身份；确可命中陈旧或拼接结果。改用结构化测试结果并绑定本次runId，不能只加一个mtime下界。对应主张：客户端测试证据只在整份日志上做三条松散正则匹配：既没有与当前 frontend 源码/`package-lock.json` 的时间或哈希绑定（陈旧日志、重放日志都能命中），也无法排除日志由多次运行拼
- 证据：scripts/collect-m10-evidence.py:68

## OCR-2024 · 部分采纳

- 位置：scripts/collect-m10-evidence.py:46；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：源码/报告的新鲜度仅以 mtime 比较判定：在粗粒度时间戳文件系统或时钟偏差下，晚于 verify 的修改仍可能满足 `<=`；而且哈希在此之后才计算，存在校验-使用间隙，evidence 中的 `sourceSha256` 并未与 verify 时真正编译的输入绑定。建议在完成校验后立即对 `sources` 计算并再 stat 一次确认未变，或改用构建时记录的哈希来比对。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：源码/报告的新鲜度仅以 mtime 比较判定：在粗粒度时间戳文件系统或时钟偏差下，晚于 verify 的修改仍可能满足 `<=`；而且哈希在此之后才计算，存在校验-使用间隙，evidence 中的 `sourceSha256` 并
- 证据：scripts/collect-m10-evidence.py:46

## OCR-2025 · 部分采纳

- 位置：scripts/collect-m10-evidence.py:52；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：`all(...)` 遍历 `browser['sourceSha256']`：当该 map 为空或只含少量条目时结果恒为 True，被截断/篡改的 browser 证据仍可产出 PASS；另外键 `p` 若为绝对路径（或含 `..`），`ROOT / p` 会丢弃 ROOT 去哈希仓库外的任意文件。建议要求条目数达到预期下限并校验每条路径落在仓库根内。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：`all(...)` 遍历 `browser['sourceSha256']`：当该 map 为空或只含少量条目时结果恒为 True，被截断/篡改的 browser 证据仍可产出 PASS；另外键 `p` 若为绝对路径（或含 `.
- 证据：scripts/collect-m10-evidence.py:52

## OCR-2026 · 部分采纳

- 位置：scripts/collect-m10-evidence.py:62；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：`ui-files.txt` 为空时 `all(...)` 恒为 True，JAR 内的 UI 资源完全未被校验；并且清单项 `f` 未做校验，含 `../` 或绝对路径时会先让 `z.read('ui/' + f)` 读到 JAR 内其他条目，再让 `ROOT / 'frontend/dist' / p` 逃出 dist 目录。建议要求清单非空且每一项都是 dist 下的相对路径。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：`ui-files.txt` 为空时 `all(...)` 恒为 True，JAR 内的 UI 资源完全未被校验；并且清单项 `f` 未做校验，含 `../` 或绝对路径时会先让 `z.read('ui/' + f)` 读到 JA
- 证据：scripts/collect-m10-evidence.py:62

## OCR-2027 · 部分采纳

- 位置：scripts/collect-m10-evidence.py:79；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：`browser['screenshots']` 为空时循环体不执行、校验直接通过，截图证据可以整体缺失；且 `image['path']` 若为绝对路径，`ROOT / path` 会得到 ROOT 之外的文件（与 `sourceSha256` 同类逃逸问题）。建议要求截图数量下限，并把路径限制在仓库目录内。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：`browser['screenshots']` 为空时循环体不执行、校验直接通过，截图证据可以整体缺失；且 `image['path']` 若为绝对路径，`ROOT / path` 会得到 ROOT 之外的文件（与 `sourc
- 证据：scripts/collect-m10-evidence.py:79

## OCR-2028 · 部分采纳

- 位置：scripts/collect-m10-evidence.py:71；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：npm audit 结果仅校验计数字段，未与 `package-lock.json` 内容绑定（BOM 通过 `jharness:npm-lock-sha256` 属性绑定，audit 完全没有），因此旧 lock 对应的审计报告可被直接复用而仍判为“无漏洞”。建议记录并校验审计时的 lock sha256，或至少要求 `audit_file` 的 mtime 不早于当前 lock。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：npm audit 结果仅校验计数字段，未与 `package-lock.json` 内容绑定（BOM 通过 `jharness:npm-lock-sha256` 属性绑定，audit 完全没有），因此旧 lock 对应的审计报告
- 证据：scripts/collect-m10-evidence.py:71

## OCR-2029 · 部分采纳

- 位置：scripts/collect-m10-evidence.py:85；OCR原严重度：low
- 计划：R0-02 / P2
- 原意见：当 `sys.argv[1]` 是绝对路径或含 `..` 时，`log.relative_to(ROOT)` 会抛 `ValueError`，脚本会在所有校验都通过、准备落盘 evidence 的位置崩溃且不产出任何记录；绝对路径还会让 evidence 里的 log 指向仓库外。建议先 resolve 并校验 `log` 位于 ROOT 内再取相对路径。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：当 `sys.argv[1]` 是绝对路径或含 `..` 时，`log.relative_to(ROOT)` 会抛 `ValueError`，脚本会在所有校验都通过、准备落盘 evidence 的位置崩溃且不产出任何记录；绝对路径
- 证据：scripts/collect-m10-evidence.py:85

## OCR-2030 · 部分采纳

- 位置：scripts/collect-m1052-evidence.py:56；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：`isinstance(c, str)` 分支让任何字符串形态的检查项无条件判为通过：若验收结果把失败项写成字符串描述（如 "history paging: FAIL"），脚本仍会输出 PASS 证据，验收校验形同虚设。建议要求每项均为映射并显式断言 `c.get('passed') is True`（或明确允许的字符串枚举集合），其余形态直接报错。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m1052-evidence.py:56

## OCR-2031 · 部分采纳

- 位置：scripts/collect-m1052-evidence.py:61；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：`ROOT / path` 没有做包含性校验：结果 JSON 中若给出绝对路径，pathlib 语义会直接丢弃 ROOT（`Path('/x') / '/etc/passwd'` -> `/etc/passwd`），`..` 段也能逃逸仓库。因此被篡改的验收 JSON 可以让脚本对仓库外任意文件取 sha256 并通过 `sourceSha256` 断言，或反过来把证据指向错误输入；此外 `data.get('sourceSha256', {})` 在字段缺失时会被静默跳过，应改为必需字段。建议 `p = (ROOT / path).resolve()` 后断言 `p.is_relative_to(ROOT)` 再计算哈希。同文件下面的 `image['path']`、`upstream.json` 的 `destination`、`ui-files.txt` 条目存在同类问题。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：`ROOT / path` 没有做包含性校验：结果 JSON 中若给出绝对路径，pathlib 语义会直接丢弃 ROOT（`Path('/x') / '/etc/passwd'` -> `/etc/passwd`），`..` 段也
- 证据：scripts/collect-m1052-evidence.py:61

## OCR-2032 · 部分采纳

- 位置：scripts/collect-m1052-evidence.py:64；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：截图路径同样未做边界校验，`image['path']` 中的绝对路径或 `../` 可逃逸 ROOT；一旦路径不存在还会以 FileNotFoundError 中断（报错原因不明确）。建议 resolve 后断言位于 ROOT 之内，并对缺失文件给出明确错误信息。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：截图路径同样未做边界校验，`image['path']` 中的绝对路径或 `../` 可逃逸 ROOT；一旦路径不存在还会以 FileNotFoundError 中断（报错原因不明确）。建议 resolve 后断言位于 ROOT
- 证据：scripts/collect-m1052-evidence.py:64

## OCR-2033 · 部分采纳

- 位置：scripts/collect-m1052-evidence.py:73；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：`destination` 来自 upstream.json，未校验边界：`../` 段可跳出 `frontend/`，Windows 上盘符绝对路径（如 `C:/x`）会让 `ROOT / 'frontend' / dest` 直接变成该绝对路径，从而让上游文件完整性校验读到预期之外的文件。建议先拒绝绝对路径/含 `..` 的段，再 resolve 并断言位于 `ROOT/'frontend'` 下。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：`destination` 来自 upstream.json，未校验边界：`../` 段可跳出 `frontend/`，Windows 上盘符绝对路径（如 `C:/x`）会让 `ROOT / 'frontend' / dest`
- 证据：scripts/collect-m1052-evidence.py:73

## OCR-2034 · 部分采纳

- 位置：scripts/collect-m1052-evidence.py:80；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：只检查清单里第一个 `.js` 条目：打包成多个 chunk 时诊断文案可能落在后续 chunk 中，断言会误判失败或（顺序不同时）检查到与诊断无关的入口文件；而当 `files` 中没有任何 `.js` 时 `next()` 抛出 StopIteration，错误信息不可读（`require` 的 ValueError 才是预期路径）。建议改为遍历全部 `.js` 条目（或拼接所有 JS 内容）来判断标记是否存在。
- 判断：该collector确用next选择首个.js检查新UI文案，当前单bundle可用，拆chunk后可误拒，缺js裸StopIteration也不清楚。采纳非空JS集合及所有受管chunk语义检查，不将未来分包当当前功能缺失。
- 证据：scripts/collect-m1052-evidence.py:80

## OCR-2035 · 采纳

- 位置：scripts/collect-m1052-evidence.py:46；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：client 日志只做正则命中，不校验时间、命令与退出码：一份历史日志（或其它构建产物的日志）只要包含 `pass 13 / fail 0 / skipped 0` 就能通过；而且该正则在日志同时包含失败行（例如 `fail 1` 但同时另有 `fail 0` 摘要）时仍会命中。建议校验该日志位于构建窗口内，并显式断言不存在失败标记（例如整体不匹配 `fail\s+[1-9]`）。
- 判断：该collector用re.search在整份客户端文本匹配分散pass/fail/skipped，未解析单轮完整摘要/退出码/构建身份；确可命中陈旧或拼接结果。改用结构化测试结果并绑定本次runId，不能只加一个mtime下界。对应主张：client 日志只做正则命中，不校验时间、命令与退出码：一份历史日志（或其它构建产物的日志）只要包含 `pass 13 / fail 0 / skipped 0` 就能通过；而且该正则在日志同时包含
- 证据：scripts/collect-m1052-evidence.py:46

## OCR-2036 · 部分采纳

- 位置：scripts/collect-m1052-evidence.py:29；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：证据新鲜度没有锚定到“本次构建”：这里只做一次子串匹配（且用 `errors='replace'`，日志损坏也能命中），任意历史遗留的 BUILD SUCCESS 日志都能通过，随后生成的 PASS 证据会把旧构建的产物当成 M10.5.2 的结果。建议同时校验时间窗（例如 `log.stat().st_mtime` 必须晚于本次采集开始、且不早于某个已知构建起点）以及日志中本次构建的标识（构建时间戳/命令/commit），并把 log 的 mtime 一并写入 evidence 供复核。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：证据新鲜度没有锚定到“本次构建”：这里只做一次子串匹配（且用 `errors='replace'`，日志损坏也能命中），任意历史遗留的 BUILD SUCCESS 日志都能通过，随后生成的 PASS 证据会把旧构建的产物当成 M1
- 证据：scripts/collect-m1052-evidence.py:29

## OCR-2037 · 采纳

- 位置：scripts/collect-m1052-evidence.py:37；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：这里只断言“报告不比日志新”，没有下界，因此任意早期的 surefire 报告（旧构建遗留）都能通过；同时客户端日志 `M10.5.2-client.log` 完全没有时间校验。触发条件：先跑一次成功的 verify（留下日志），之后源码/依赖变更或残留旧 target 目录，脚本仍会给出 PASS。建议把构建窗口固化为 [构建开始, 日志 mtime]，要求所有报告与 client 日志的 mtime 都落在该窗口内，并断言 `M10.5.2-client.log` 的时间不早于 verify 日志。
- 判断：该collector用re.search在整份客户端文本匹配分散pass/fail/skipped，未解析单轮完整摘要/退出码/构建身份；确可命中陈旧或拼接结果。改用结构化测试结果并绑定本次runId，不能只加一个mtime下界。对应主张：这里只断言“报告不比日志新”，没有下界，因此任意早期的 surefire 报告（旧构建遗留）都能通过；同时客户端日志 `M10.5.2-client.log` 完全没有时间校验。触发条件：先跑一次成功
- 证据：scripts/collect-m1052-evidence.py:37

## OCR-2038 · 部分采纳

- 位置：scripts/collect-m1052-evidence.py:39；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：tests/failures/errors/skipped 直接取自 surefire XML 的头部属性，从不与 `cases` 实际条数交叉校验，也不校验每个 module 都产生了报告：只要属性写成 331/0/0/0，即使 XML 里没有任何 `<testcase>`（或被删减）也能满足后面的 totals 断言。建议断言 `len(cases) == tests` 且在 totals 前校验 `len(suites) == len(modules)`（每个 module 至少一个报告）。
- 判断：XML头计数未与testcase数逐suite核对是真实缺口；采纳共享当前收集器增加数量/必需case集合校验。不采纳len(suites)==len(modules)，模块有多suite且部分生产模块测试在testkit集中，强制每module一报告错误。旧阶段331常量不改作新验收。
- 证据：scripts/collect-m1052-evidence.py:39

## OCR-2039 · 不采纳

- 位置：scripts/collect-m1052-evidence.py:84；OCR原严重度：medium
- 计划：无
- 原意见：前端输入清单是“尽力而为”的：`rglob` 结果里的目录与缺失的 `frontend/src` 都被 `if p.is_file()` 静默丢弃，追加的 tests/scripts 列表（settings.mjs、browser.mjs、client.test.ts、package-ui.mjs、diagnosis.mjs、history.mjs）即使缺失也不会报错，因此“前端输入漂移”证据可能在文件缺失或采集前被改动的状态下仍然生成 PASS。建议对每个必选文件 `require(p.is_file())`，并断言 `frontend/src` 的 rglob 结果非空。
- 判断：该M10.5.2历史collector按当时文件集采集并在JAR/UI步骤检查关键业务类与资源，不应为已结束快照追溯扩大必需输入；当前M10.5.9有独立完整inputs。未展示原记录缺文件实际通过，缺失必需输入的现行统一门禁另处理。
- 证据：scripts/collect-m1052-evidence.py:84

## OCR-2040 · 部分采纳

- 位置：scripts/collect-m1054-native-evidence.py:62；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：`ui-files.txt` 中的条目被未经校验地拼接为 `'ui/' + p` 并用于 `root / 'frontend/dist' / p`。若清单包含 `..`、绝对路径或空行，路径会逃逸出 `frontend/dist`（读取任意文件并写入报告哈希，属文件系统边界问题），空行/非法条目则只会在第 63 行的集合比较中产生误导性的 'Stale UI in JAR' 或导致后续 `archive.read` 抛 KeyError。建议先对每个条目做非空、相对路径、无 `..` 的断言。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：`ui-files.txt` 中的条目被未经校验地拼接为 `'ui/' + p` 并用于 `root / 'frontend/dist' / p`。若清单包含 `..`、绝对路径或空行，路径会逃逸出 `frontend/dist`
- 证据：scripts/collect-m1054-native-evidence.py:62

## OCR-2041 · 采纳

- 位置：scripts/collect-m1054-native-evidence.py:42；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：客户端日志校验的正则未锚定，且 `\s` 会跨行匹配：`bypass 16`、乃至上一行以 `pass` 结尾而下一行以 `16` 开头的内容都能通过，导致“回归失败”的日志被当成通过。此外第 72 行 `client=dict(tests=16, failures=0, skipped=0, ...)` 是硬编码值，并非从日志解析，一旦日志内容与硬编码不一致，报告就会写入未经证实的数字。建议锚定整行（MULTILINE）并从匹配组中取值再比对。
- 判断：该collector用re.search在整份客户端文本匹配分散pass/fail/skipped，未解析单轮完整摘要/退出码/构建身份；确可命中陈旧或拼接结果。改用结构化测试结果并绑定本次runId，不能只加一个mtime下界。对应主张：客户端日志校验的正则未锚定，且 `\s` 会跨行匹配：`bypass 16`、乃至上一行以 `pass` 结尾而下一行以 `16` 开头的内容都能通过，导致“回归失败”的日志被当成通过。此外第 72
- 证据：scripts/collect-m1054-native-evidence.py:42

## OCR-2042 · 部分采纳

- 位置：scripts/collect-m1054-native-evidence.py:34；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：Surefire 报告本身没有做时效/归属校验：只比较了 Java 源码与验证日志的 mtime（第 31 行），而 `TEST-*.xml` 既不检查 mtime 是否晚于 log，也没有与本次构建（sessionId/时间戳）绑定，还不校验产生报告的模块集合。触发条件：本轮只跑了部分模块（如 `mvn -pl harness-cli test`）或在跳过测试的方式下构建，`target/surefire-reports` 中残留的旧报告仍可满足第 39 行硬编码的 338/0/0/0 总数，垃圾证据被判为合格。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：Surefire 报告本身没有做时效/归属校验：只比较了 Java 源码与验证日志的 mtime（第 31 行），而 `TEST-*.xml` 既不检查 mtime 是否晚于 log，也没有与本次构建（sessionId/时间戳）
- 证据：scripts/collect-m1054-native-evidence.py:34

## OCR-2043 · 部分采纳

- 位置：scripts/collect-m1054-native-evidence.py:44；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：JAR 与源码之间缺少构建时序/内容绑定：从未比较 JAR 的 mtime 与源码 mtime，也没有校验 class 与当前源码一致。第 49 行只做 JAR↔原生记录的哈希一致性，因此触发条件是“源码再次修改但未重新打包 + 用同一旧 JAR 重跑原生探针”，此时 jarSha256 与 sourceSha256 全都一致，门禁通过而证据来自过期产物。建议要求 JAR 不早于任何受管源码，或校验 JAR 内 class 摘要。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：JAR 与源码之间缺少构建时序/内容绑定：从未比较 JAR 的 mtime 与源码 mtime，也没有校验 class 与当前源码一致。第 49 行只做 JAR↔原生记录的哈希一致性，因此触发条件是“源码再次修改但未重新打包 +
- 证据：scripts/collect-m1054-native-evidence.py:44

## OCR-2044 · 部分采纳

- 位置：scripts/collect-m1054-native-evidence.py:55；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：缺失服务/服务锁定分支完全信任探针自报的 `expectationMet`/`expectedUnavailable`/`passed` 布尔量，没有独立证据说明“不可用”是由预期原因（服务缺失、服务被锁定）造成的。触发条件：探针因安装/路径错误或真实持久化缺陷返回 `passed=False and expectedUnavailable=True`，本门禁会把“持久化未完成”当成“按预期不可用”放行。建议同时校验失败原因枚举/退出码并断言凭据未被写入的路径。
- 判断：workflow确显式构造DBus缺失与锁定，probe又验证unavailable/checks；不能将三场景说成仅布尔伪造。采纳在现行合并门禁复核不可用receipt的完整一致性，避免顶层标志与子字段矛盾，根因不是任意异常都算不可用。
- 证据：scripts/collect-m1054-native-evidence.py:55；scripts/test-native-credentials.py:24；.github/workflows/native-credentials.yml:69

## OCR-2045 · 不采纳

- 位置：scripts/collect-m1054-native-evidence.py:41；OCR原严重度：low
- 计划：无
- 原意见：所有证据文件都是先读后判：`client_log`、`M10.5.4-native-*.json`、`M10-settings-browser-results.json`、最终 verify 日志在缺失时直接抛 FileNotFoundError/Traceback，而不是给出明确的校验失败信息（`require` 只覆盖“内容不满足”的情况）；另外 `destination` 目录未做存在性保证，写入时同样会抛裸异常。建议在读取前用 `require(path.is_file(), ...)` 统一守卫并 `destination.mkdir(parents=True, exist_ok=True)`。
- 判断：缺证据FileNotFoundError已非零封闭退出，不会写PASS；docs/testing在仓库必有。统一错误文案/mkdir仅便利性，非当前门禁绕过，不逐一修改已结束的历史collector。
- 证据：scripts/collect-m1054-native-evidence.py:41

## OCR-2046 · 部分采纳

- 位置：scripts/build-candidate.py:83；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：manifest 的 `sourceSha256` 用前缀/后缀白名单挑选构建输入：`frontend/` 下除 `src/` 之外的构建输入（package.json、tsconfig/vite 配置等）、`.mvn/`、`third-party/inventory.json` 等都被排除，同时脚本没有任何一步把 `lib/jharness.jar` 与这些源码绑定（仅记录了 jar 的哈希和 `workingTreeDirty` 布尔值，也不据此拒绝脏工作区）。结果是陈旧 JAR 或由未收录输入构建出的 JAR 可以携带一份看起来完整的 sourceSha256 通过。建议至少在 `workingTreeDirty` 为 True 时拒绝出包，并让源码清单直接来自构建系统产出的完整输入列表，而不是路径模式匹配。
- 判断：candidate manifest源白名单未含完整frontend构建输入/.mvn且jar仅独立哈希；采纳从实际构建run清单生成完整身份。workingTreeDirty本地候选可记录，最终CI聚合另拒dirty，不能一律禁止本地准备。
- 证据：scripts/build-candidate.py:83

## OCR-2047 · 部分采纳

- 位置：scripts/build-candidate.py:40；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：`rglob` 没有过滤符号链接，`third-party/` 下的符号链接文件会以其树内路径被打包，但内容来自链接目标（可能在仓库之外）；`relative_to(ROOT)` 只校验路径前缀，无法发现这种越界读取。建议对 `path.is_symlink()` 直接报错，或用 `path.resolve()` 做包含校验后再打包。
- 判断：third-party目录/清单路径read_bytes会跟链接且未统一真实根约束，采纳常规文件/边界与合法相对条目校验。输入为本地受管资源，不标远程任意读漏洞。
- 证据：scripts/build-candidate.py:40

## OCR-2048 · 部分采纳

- 位置：scripts/build-candidate.py:62；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：这里只校验了"源 third-party 文件都在 shaded JAR 中"这一个方向，没有反向校验 JAR 的 `META-INF/jharness/third-party/` 下不存在多余条目。shaded JAR 里残留的陈旧或未授权的 legal 文件（例如已从 inventory 删除的许可证）会随候选包一起发布并通过本检查。建议比较集合： ```python jar_legal = {n[len("META-INF/jharness/"):] for n in archive.namelist() if n.startswith("META-INF/jharness/third-party/")} if jar_legal != {n for n in files if n.startswith("third-party/")}:     raise ValueError("Legal file set mismatch between shaded JAR and third-party/") ```
- 判断：JAR legal仅检查源→包正向，可能留多余旧legal，采纳双向精确集合对比；多余许可证不等同已证明未授权代码发布。
- 证据：scripts/build-candidate.py:62

## OCR-2049 · 部分采纳

- 位置：scripts/build-candidate.py:31；OCR原严重度：high
- 计划：R6-01 / P2
- 原意见：`legal["file"]`（以及第 34 行的 `upstream["file"]`）直接拼接到 `third-party` 下读取，没有任何路径包含校验。若清单中出现绝对路径、`../` 或指到树外的符号链接，校验会读取 `third-party/` 之外的文件，而第 40-42 行的打包循环只收录 `third-party/` 树内文件，于是会出现"校验通过了某个 legal 资源、但该资源根本没进候选包"的情况，`META-INF/jharness/` 的一致性检查也无法发现。建议先解析再做包含校验： ```python p = (ROOT / "third-party" / legal["file"]).resolve() if not p.is_relative_to((ROOT / "third-party").resolve()):     raise ValueError("Legal resource outside third-party: " + legal["file"]) ``` upstream 的处理同理。
- 判断：third-party目录/清单路径read_bytes会跟链接且未统一真实根约束，采纳常规文件/边界与合法相对条目校验。输入为本地受管资源，不标远程任意读漏洞。
- 证据：scripts/build-candidate.py:31

## OCR-2050 · 采纳

- 位置：scripts/build-candidate.py:22；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：字典推导会静默折叠重复坐标，且第 24 行的 `set(external)` 比较也只比较键集合。当 BOM 中同一 `group:name:version` 出现两条（hashes 不同）的组件时，只有最后一条参与第 27-29 行的 SHA-256 比对，靠前那条的 JAR 哈希从未被校验，原件 Hash 绑定可被绕过。建议先显式检测重复： ```python coords = [f'{c["group"]}:{c["name"]}:{c["version"]}' for c in json.loads(bom)["components"] if c["group"] != "io.jharness"] if len(coords) != len(set(coords)):     raise ValueError("Duplicate BOM component coordinate") ```
- 判断：BOM坐标字典推导会折叠重复组件/hash，集合比较不发现重复；应先校验唯一坐标与哈希算法一致。
- 证据：scripts/build-candidate.py:22

## OCR-2051 · 采纳

- 位置：scripts/build-candidate.py:96；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：`zipfile.ZipInfo` 的 `create_system` 默认值依赖平台（Windows 上为 0），而解压工具只有在 `create_system == 3` 时才会解释 `external_attr` 高 16 位里的 Unix 权限位。脚本本身还生成了 `jharness.cmd`，说明存在在 Windows 上构建的路径，此时 `jharness` 启动脚本解压后不会是 0755，安装包启动脚本可能不可执行。建议显式指定： ```python info.create_system = 3 info.external_attr = (0o100755 if name == "jharness" else 0o100644) << 16 ```
- 判断：ZipInfo未设置create_system却只设Unix external_attr，Windows构建包可能缺POSIX可执行属性。应显式Unix归档语义并跨平台解压启动验证。
- 证据：scripts/build-candidate.py:96

## OCR-2052 · 部分采纳

- 位置：scripts/collect-m1054-evidence.py:52；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：第 54 行：`isinstance(c, str) or c.get('passed')` 会把任意字符串条目无条件视为“通过”，只要条目是字符串就能绕过 passed 校验；同时若 checks 中出现 None/数字等既非 str 也非 dict 的元素，`c.get` 会抛 AttributeError。建议显式要求每条检查都是 dict 且 `passed` 为真（或对字符串条目做明确的白名单校验）。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m1054-evidence.py:52

## OCR-2053 · 部分采纳

- 位置：scripts/collect-m1054-evidence.py:58；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：验收 JSON 中的路径（screenshots[].path、sourceSha256 的 key，以及后面 ui-files.txt、upstream.json 的 destination）未经边界校验就直接与 ROOT 拼接。`Path('/abs/path')` 表示绝对路径时会直接丢弃 ROOT，含 `..` 的相对项也会逃逸仓库根目录，因此校验器可能对仓库外任意文件计算哈希并“通过”漂移断言，等于把证据绑定到了非受控文件（触发条件：缓存 JSON 或 upstream.json 被替换/写错）。建议统一封装解析函数，resolve 后强制校验仍在 ROOT 之内。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：验收 JSON 中的路径（screenshots[].path、sourceSha256 的 key，以及后面 ui-files.txt、upstream.json 的 destination）未经边界校验就直接与 ROOT 拼接
- 证据：scripts/collect-m1054-evidence.py:58

## OCR-2054 · 部分采纳

- 位置：scripts/collect-m1054-evidence.py:92；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：第 94 行与第 102 行：`ui-files.txt` 中的 `p`、`upstream.json` 的 `destination` 同样直接拼接（`frontend/dist` / `frontend`），绝对路径或 `..` 条目会逃逸出 `frontend` 目录，使用仓库外文件与新 JAR 比对“一致”；这与第 61-63 行的路径边界问题相同，需要同样的 resolve + containment 校验。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：第 94 行与第 102 行：`ui-files.txt` 中的 `p`、`upstream.json` 的 `destination` 同样直接拼接（`frontend/dist` / `frontend`），绝对路径或 `..
- 证据：scripts/collect-m1054-evidence.py:92

## OCR-2055 · 部分采纳

- 位置：scripts/collect-m1054-evidence.py:93；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：第 95 行：`next(...)` 只取 `ui-files.txt` 中第一个 .js 文件做字符串检查。若前端被拆分为多个 chunk（例如 vendor/chunk 分包，或关键文案在非首个 bundle 中），第 96 行的新 UI 文案断言会漏检；而一旦没有 .js 条目，`next()` 会抛 StopIteration（非 ValueError），使校验脚本以堆栈而不是明确的证据失败信息退出。建议先取出全部 js 条目、显式断言非空并拼接后再检查。
- 判断：collector只next首个.js做文案断言，当前单bundle能用但拆分后误拒；缺.js则裸StopIteration。采纳明确非空JS集合及所有受管chunk检查，不把未来分包当当前功能失败。
- 证据：scripts/collect-m1054-evidence.py:93

## OCR-2056 · 部分采纳

- 位置：scripts/collect-m1054-evidence.py:40；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：第 40 行：新鲜度只覆盖 `pom.xml` 与 `harness-*/src/**/*.java`，但第 107 行仍把 `frontend/src`、`frontend/tests` 的哈希写入 `frontendSha256` 作为证据。前端源码在 verify 之后被修改时不会触发任何断言，最终会发布“构建成功 + 未构建的前端源码”这种不一致证据（第 94 行的 UI/JAR 比对只覆盖 `frontend/dist`，也不覆盖源码）。建议把前端源码一起纳入与 log 的 mtime 比较。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：第 40 行：新鲜度只覆盖 `pom.xml` 与 `harness-*/src/**/*.java`，但第 107 行仍把 `frontend/src`、`frontend/tests` 的哈希写入 `frontendSha25
- 证据：scripts/collect-m1054-evidence.py:40

## OCR-2057 · 部分采纳

- 位置：scripts/collect-m1054-evidence.py:87；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：第 89 行：`any(...)` 是对每个文件独立在全部历史 manifest 中查找匹配。若 SystemCredentials.java 来自 manifest A、NativeCredentials.java 来自 manifest B，两个文件各自都能命中，检查依然通过，但“同一份平台 CI 证据同时覆盖两个文件”这一前提并不成立。建议要求存在单个 manifest 同时匹配这两个条目，例如 `require(any(all(m.get(n) == sha(ROOT / n) for n in native_paths) for m in historical), ...)`。
- 判断：原生实现文件逐个any历史manifest匹配，不能证明同一份三平台来源同时覆盖完整实现集；采纳完整native源集合+探针脚本+历史run身份一起匹配，仍明确历史专项不替代本次全量CI。
- 证据：scripts/collect-m1054-evidence.py:87

## OCR-2058 · 部分采纳

- 位置：scripts/collect-m1054-evidence.py:64；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：第 66 行（以及第 71、114 行）在全部校验（native 身份、JAR/UI、upstream）执行完成之前就把记录写入 `docs/testing`。一旦后续任一 `require` 失败，`docs/testing` 里会残留新一批 records，而 `M10.5.4-results.json` 仍是旧版本，形成互相矛盾的已发布证据，且无法区分哪次运行产生的；这与“先完成全部校验、再原子发布”的证据绑定要求相悖。建议先在内存/临时目录中暂存所有产物，全部断言通过后再统一落盘（或先写临时文件再 rename）。
- 判断：collector在后续native/JAR/UI/upstream全部校验之前直接覆盖部分docs/testing记录，后段失败可留下新旧混合。采纳全部验证后staging与manifest最后原子发布，失败保留明确回执，历史事实不重造。
- 证据：scripts/collect-m1054-evidence.py:64

## OCR-2059 · 部分采纳

- 位置：scripts/collect-m1054-evidence.py:53；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：第 55 行：`data.get('runtime', {})` 只在 key 缺失时回退，若 JSON 中存在 `"runtime": null`（或以列表/字符串形式出现），`.get('paidRequests')` 会抛出 AttributeError，使本就期望的 ValueError 校验变成未捕获异常、直接中断整个证据收集流程，且此时 docs/testing 中已有文件被覆盖（见第 66 行）。建议对 runtime 做类型判断。
- 判断：runtime存在为null/非object时data.get(runtime,{})不回退，接着.get会AttributeError而非语义校验；仍非零fail-closed。采纳严格嵌套schema与失败回执，避免前段归档残留误用。
- 证据：scripts/collect-m1054-evidence.py:53

## OCR-2060 · 部分采纳

- 位置：scripts/collect-m1054-native-ci-evidence.py:82；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：第 79 行只校验了「源码 mtime <= 日志 mtime」，但 surefire 报告与日志、源码之间没有任何时间或哈希绑定。触发条件：有人用 `-DskipTests` 重新打包生成含 `BUILD SUCCESS` 的日志（或直接沿用旧日志），而 `harness-*/target/surefire-reports/TEST-*.xml` 是更早一轮、更早源码状态留下的产物；此时 tests=338/failures=0 的总数校验照样通过，过期测试结果被记入 PASS 证据。建议在解析前要求每个 surefire XML 的 mtime 不早于最新源码 mtime（或校验报告的生成时间）。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：第 79 行只校验了「源码 mtime <= 日志 mtime」，但 surefire 报告与日志、源码之间没有任何时间或哈希绑定。触发条件：有人用 `-DskipTests` 重新打包生成含 `BUILD SUCCESS` 的日
- 证据：scripts/collect-m1054-native-ci-evidence.py:82

## OCR-2061 · 不采纳

- 位置：scripts/collect-m1054-native-ci-evidence.py:35；OCR原严重度：medium
- 计划：无
- 原意见：与已测试 commit 的差异校验只覆盖 `*.java`、`pom.xml`、`*/pom.xml`，未覆盖 `frontend/`、`ui/` 资源、`scripts/test-native-credentials.py` 等非 Java 资产；而第 94-96 行的 UI 校验只是「JAR 内的 ui/* 与本地 `frontend/dist` 一致」，`frontend/dist` 及 `frontend/src` 本身没有与 `commit` 绑定。触发条件：CI 跑完后本地修改前端源码（未提交、或 dist 未重建/被手工替换），JAR 与 dist 仍然一致，脚本会给出「UI 已验证」的 PASS 证据，但对应 commit 的前端并非被验证的内容。建议把前端源码/资源也纳入 diff 比对，或改为直接对 `git show commit:<path>` 计算 UI 哈希。
- 判断：本专项明确只认证原生credentials增量，sourceCommit绑定280份Java/POM；本地UI/JAR一致性另为本地回归，不宣称CI全前端矩阵通过。将当前全树发布要求套此历史原生专项会扩大已批准认证范围。
- 证据：scripts/collect-m1054-native-ci-evidence.py:35

## OCR-2062 · 采纳

- 位置：scripts/collect-m1054-native-ci-evidence.py:72；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：对 missing-service / locked-service 收据只校验了 `passed is False`、`expectedUnavailable` 与非零 `exitCode`，没有校验收据中的 `credentialStore` 与 `checks` 内容。触发条件：收据的 `checks` 中出现「凭据已持久化」这类正向结果（例如 `checks` 为真值而非 `{'cleanup': False}`），或 `credentialStore` 不是 `unavailable`，只要顶层三个字段正确就会被当作「不可用已正确判定」通过——即矛盾收据被放行，UNKNOWN 状态可能被记成合规结果。建议按探测脚本的期望语义显式断言 `credentialStore == 'unavailable'` 且 `checks == {'cleanup': False}`。
- 判断：CI合并器只看expectedUnavailable/passed/exitCode，而producer本已要求credentialStore=unavailable且checks={cleanup:false}；合并器应重验这一一致性，不能盲信expectationMet，否则可接受内部矛盾receipt。不把同Java探针的三种真实环境说成伪造。
- 证据：scripts/collect-m1054-native-ci-evidence.py:72；scripts/test-native-credentials.py:24

## OCR-2063 · 部分采纳

- 位置：scripts/collect-m1054-native-ci-evidence.py:86；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：本地原生凭据收据（`.build-cache/M10.5.4-native-credentials.json`）只校验了 `jarSha256` / `sourceSha256` / `passed` / `expectationMet`，没有校验 `probeScriptSha256`、`exitCode` 与 `expectedUnavailable`。触发条件：`scripts/test-native-credentials.py` 在收据生成后被修改（探测逻辑变化），只要 jar 与 source 未变，旧收据仍被视为有效，第 110 行会把这份过期探测结果写入 PASS 证据。远端收据在第 67 行做了 `probeScriptSha256` 比对，本地收据应与之一致，并同时确认 `exitCode == 0` 且 `expectedUnavailable` 为 false（否则 UNKNOWN/不可用状态可能被当作通过）。
- 判断：本地native收据检查jar/source/passed但未验证probeScriptSha256及exit/expectedUnavailable一致性；采纳探针源码身份+明确场景schema，正常credentials必须真实成功而非预期不可用。
- 证据：scripts/collect-m1054-native-ci-evidence.py:86

## OCR-2064 · 部分采纳

- 位置：scripts/collect-m1053-evidence.py:56；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：验收闸门可被绕过：`isinstance(c, str)` 会把任意字符串检查项（例如 `"FAIL: ..."`、`"skipped"`）一律当作通过；即使检查项是字典，`c.get('passed')` 也只做真值判断，`1`、`"false"`、`"no"` 等非布尔值同样通过。该行决定 M10.5.3 是否记为 PASS，建议只接受显式的布尔 `True`，并拒绝字符串形式的检查项，避免测试脚本写入错误格式时给出假阳性证据。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m1053-evidence.py:56

## OCR-2065 · 部分采纳

- 位置：scripts/collect-m1053-evidence.py:82；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：`next(p for p in files if p.endswith('.js'))` 只取 `ui-files.txt` 中第一个 `.js` 条目：一旦打包产物拆分成多个 JS chunk 或条目顺序变化，「为什么结果待核实」落在其他 chunk 时会出现误报失败，且只校验了一个文件本身也不完整；当 `files` 中没有 `.js` 时还会抛出未处理的 `StopIteration`。建议改为对全部 JS 条目做 `any(...)` 校验，并在缺失时给出明确错误信息。
- 判断：该collector确用next选择首个.js检查新UI文案，当前单bundle可用，拆chunk后可误拒，缺js裸StopIteration也不清楚。采纳非空JS集合及所有受管chunk语义检查，不将未来分包当当前功能缺失。
- 证据：scripts/collect-m1053-evidence.py:82

## OCR-2066 · 采纳

- 位置：scripts/collect-m1053-evidence.py:47；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：客户端测试日志仅靠 `pass 16` / `fail 0` / `skipped 0` 三个子串匹配判定，既没有像 Surefire 报告那样校验 mtime（旧日志可被原样重放，只要残留一次通过的输出即可满足），也没有与 JAR/前端源码建立任何绑定关系。建议至少要求 `client.stat().st_mtime` 不早于被测前端源码与测试文件的修改时间（或直接要求晚于本次 verify 日志），否则在未真正运行客户端测试的情况下仍能生成 PASS 证据。
- 判断：该collector用re.search在整份客户端文本匹配分散pass/fail/skipped，未解析单轮完整摘要/退出码/构建身份；确可命中陈旧或拼接结果。改用结构化测试结果并绑定本次runId，不能只加一个mtime下界。对应主张：客户端测试日志仅靠 `pass 16` / `fail 0` / `skipped 0` 三个子串匹配判定，既没有像 Surefire 报告那样校验 mtime（旧日志可被原样重放，只要残留一次通过的
- 证据：scripts/collect-m1053-evidence.py:47

## OCR-2067 · 部分采纳

- 位置：scripts/collect-m1053-evidence.py:49；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：JAR 没有被绑定到本次 verify 运行：源码与 POM 都要求 `mtime <= log.mtime`，但 `jar` 本身没有该约束。若在 verify 之后重新打包（例如 skipTests 重新构建或换 JDK/参数构建），源码 mtime 检查依然通过，而后续验收回执会以新 JAR 的 hash 为准重新记录，于是“未经 Java 全量测试的 JAR”被认证为已通过。建议对 `jar` 增加与 verify 日志一致的 mtime 约束（`require(jar.stat().st_mtime <= log.stat().st_mtime, ...)`）。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：JAR 没有被绑定到本次 verify 运行：源码与 POM 都要求 `mtime <= log.mtime`，但 `jar` 本身没有该约束。若在 verify 之后重新打包（例如 skipTests 重新构建或换 JDK/参数
- 证据：scripts/collect-m1053-evidence.py:49

## OCR-2068 · 部分采纳

- 位置：scripts/collect-m1053-evidence.py:43；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：用例总数校验只比对 337 这个硬编码值，而 Surefire 报告仅要求「不晚于 verify 日志」。若在之前某次全量 verify 之后又执行了只覆盖部分模块的构建（例如 `mvn -pl harness-cli test`），新日志含 BUILD SUCCESS，旧的全量报告因 mtime 更早仍被统计，`totals == 337` 依然成立，从而把一次不完整的验证记为 Java 全量通过。建议额外要求 verify 日志的 reactor summary 中每个 `<module>` 都出现 SUCCESS（或要求每份 Surefire 报告都晚于本次构建开始时间）。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：用例总数校验只比对 337 这个硬编码值，而 Surefire 报告仅要求「不晚于 verify 日志」。若在之前某次全量 verify 之后又执行了只覆盖部分模块的构建（例如 `mvn -pl harness-cli test`
- 证据：scripts/collect-m1053-evidence.py:43

## OCR-2069 · 不采纳

- 位置：scripts/collect-m1053-evidence.py:61；OCR原严重度：medium
- 计划：无
- 原意见：`data.get('sourceSha256', {})` / `data.get('screenshots', [])` 使用空默认值，因此当验收回执缺失这两个字段（或仅写入空对象/空数组）时循环一次都不执行，输入漂移与截图漂移校验被静默跳过，最终仍输出 PASS。建议把字段存在且非空作为前置条件（`require(data.get('sourceSha256'), ...)`、`require(data.get('screenshots'), ...)`），确保每份回执确实带有可校验的溯源信息。
- 判断：此collector同时读settings/browser/launcher/diagnosis/history/turns，某些收据按当时契约无sourceSha256或无screenshots（launcher）。强制每份两字段非空会错误拒绝合法收据；应在现行按fixture定义必需字段，不能一刀切追溯修旧档。
- 证据：scripts/collect-m1053-evidence.py:61

## OCR-2070 · 部分采纳

- 位置：scripts/collect-m1053-evidence.py:88；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：上游基线自身未被固定：第 73-75 行用 `frontend/upstream.json` 校验上游文件 hash，但 `frontendInputsSha256` 只覆盖 `frontend/src` 与显式列出的文件，不包含 `upstream.json`。因此同时篡改基线清单和被引用文件即可通过校验，来源/许可证据失去防篡改意义。建议将 `'upstream.json'` 加入该显式文件列表。
- 判断：前端输入manifest未纳upstream.json这份来源基线，采集时不能证明所校验基线身份；补完整基线/许可/构建脚本输入摘要。受管Git仍提供审查来源，不声称任何同改baseline都是可防恶意篡改。
- 证据：scripts/collect-m1053-evidence.py:88

## OCR-2071 · 部分采纳

- 位置：scripts/collect-release-evidence.py:60；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：证据写到 `ROOT/target/release-evidence.json`，但未确保父目录存在：干净 checkout 上（或 `target/` 被清理后只重跑本脚本时）`write_text` 会抛 FileNotFoundError，而此时完整验证已跑完，证据直接丢失。写入也非原子操作，中途异常会留下被截断的 JSON，下游读取会解析失败。建议先建目录，再写临时文件后 `os.replace`。
- 判断：源码在全部校验完成前逐份覆盖docs/testing或原地写最终JSON，后段失败可留半更新证据；采纳先验证/同一字节快照再staging与manifest最后原子发布，失败保留明确状态。历史已归档内容不重造。
- 证据：scripts/collect-release-evidence.py:60

## OCR-2072 · 部分采纳

- 位置：scripts/collect-release-evidence.py:20；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：`sys.argv[1]` 未做参数个数校验：缺参时直接抛 IndexError 且没有用法提示。另外 `ROOT / sys.argv[1]` 的行为也不可控——传入绝对路径时 pathlib 会丢弃 ROOT，传入 `../..` 也不会被限制，于是任意位置只要含 `[INFO] BUILD SUCCESS` 的文件都能被当作“验证日志”，其路径还会原样写进证据的 `verificationLog`。建议先校验参数个数，并把解析后的日志路径限制在仓库内。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：`sys.argv[1]` 未做参数个数校验：缺参时直接抛 IndexError 且没有用法提示。另外 `ROOT / sys.argv[1]` 的行为也不可控——传入绝对路径时 pathlib 会丢弃 ROOT，传入 `../.
- 证据：scripts/collect-release-evidence.py:20

## OCR-2073 · 部分采纳

- 位置：scripts/collect-release-evidence.py:38；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：门禁只校验全局总数 296 与 failures/errors/skipped 为 0，存在两处漏洞：(1) 某模块的 `target/surefire-reports` 缺失或为空（未跑测试、被 profile 排除、`-DskipTests`）时会被静默跳过，只要其它模块凑够 296 条即通过，证据里完全不会出现该模块，无法反映“每个 runner 的完整 clean-verify”；(2) totals 取自 XML 声明的 `tests` 等属性（缺失时默认 0），未与真实 `<testcase>` 数量（`cases` 长度）交叉校验，残留旧属性或篡改属性都能通过；同时 296 这个阈值与 milestone 参数脱钩，选 M10.5.9 时仍用 M9 的下限。建议逐模块要求至少一份报告、校验声明计数等于实际用例数、并按 milestone 配置阈值。
- 判断：共享release collector确只以>=296及XML头计数，采纳按milestone检查实际testcase集合/计数，且旧296不充当M10.5.9完整认证。拒绝每模块至少一suite的建议，实际集中testkit；M10.5.9另有410精确门禁，原意见不能证明当前M11绕过。
- 证据：scripts/collect-release-evidence.py:38；scripts/collect-m1059-evidence.py:28

## OCR-2074 · 部分采纳

- 位置：scripts/collect-release-evidence.py:46；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：`os.environ["JAVA_HOME"]` 直接下标访问：变量缺失时抛 KeyError 且无任何提示；值为空串时会退化成相对路径 `bin/java`，取决于当前工作目录。另外后面 `subprocess.run([java_cmd, "-version"], ...)` 的 returncode 从未检查，java 调用失败（文件不存在之外的原因）时 `javaRuntime` 记录空串或错误输出，证据仍照常生成。建议显式校验 JAVA_HOME 与可执行文件，并检查 returncode。
- 判断：JAVA_HOME下标和-version未check返回码确可留下无效javaRuntime；采纳共享release收集器明确可执行文件/退出状态/版本非空，不能把环境缺失时非零异常说成假通过。
- 证据：scripts/collect-release-evidence.py:46

## OCR-2075 · 部分采纳

- 位置：scripts/collect-release-evidence.py:33；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：Missing `name`/`value` attributes yield None that is mixed with strings and later passed to `sorted()`, raising `TypeError: '<' not supported between instances of 'str' and 'NoneType'`; conversely an empty java-version set is silently written as an empty list. Filter/reject None and fail explicitly when the set is empty.
- 判断：java.version缺value会把None并入集合后sorted与str冲突，完全缺版本也输出空list。采纳拒绝缺/空版本并给明确诊断，不默默过滤后制造完整元数据。
- 证据：scripts/collect-release-evidence.py:33

## OCR-2076 · 部分采纳

- 位置：scripts/collect-release-evidence.py:29；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：用日志文件的 mtime 作为“验证完成时刻”并不能证明输入未被改动：日志是在构建结束时写入的，若某模块在编译之后、构建结束之前源码被修改，其 mtime 仍早于日志 mtime，检查会通过，但证据记录的是未被测试的输入。此外严格 `>` 使时间戳完全相等（粗粒度文件系统、并行 checkout）的情况也被判为合法。建议以构建开始时刻作为基线（由 verify 脚本先落一个 timestamp 文件），并用 `>=` 处理同秒粒度。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：用日志文件的 mtime 作为“验证完成时刻”并不能证明输入未被改动：日志是在构建结束时写入的，若某模块在编译之后、构建结束之前源码被修改，其 mtime 仍早于日志 mtime，检查会通过，但证据记录的是未被测试的输入。此外严格
- 证据：scripts/collect-release-evidence.py:29

## OCR-2077 · 不采纳

- 位置：scripts/collect-release-evidence.py:57；OCR原严重度：medium
- 计划：无
- 原意见：这里仅在 `GITHUB_RUN_ID` 存在时读取 `os.environ["GITHUB_REPOSITORY"]`，若本地或非 GitHub 环境只继承了 RUN_ID 就会抛 KeyError；同时 `subprocess.check_output(["git", ...])` 在非 git 仓库、无 git 可执行文件或 git 返回非零时抛 CalledProcessError，异常信息不含 stderr，定位困难。建议两个变量都用 get() 判空后再拼接，并对 git 调用做 try/except 或 check=True + 显式错误提示。
- 判断：GitHub CI同时提供RUN_ID/REPOSITORY，git在本仓库运行；不满足环境时KeyError/CalledProcessError已经非零拒绝。假想部分环境变量/不在Git仓库并不是PASS绕过，不为通用外部运行改共享约束。
- 证据：scripts/collect-release-evidence.py:57

## OCR-2078 · 部分采纳

- 位置：scripts/collect-m1055-evidence.py:52；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：第 52 行的浏览器验收门可被绕过：`isinstance(c, str)` 使任意字符串条目（包括 "fail"、"skipped" 之类的失败标签）都被视为已通过，只有 dict 条目才会检查 `passed`；若 checks 中出现非 str/dict 元素（None、数字），还会抛 AttributeError 而不是可读的校验失败。触发条件：任一验收脚本以字符串形式写入一条失败/跳过记录，该产物仍会被复制进 docs/testing 并计入 M10.5.5 证据。建议只接受带 `passed is True` 的 dict 记录。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m1055-evidence.py:52

## OCR-2079 · 部分采纳

- 位置：scripts/collect-m1055-evidence.py:93；OCR原严重度：low
- 计划：R0-01 / P3
- 原意见：第 93 行 `next(p for p in files if p.endswith('.js'))` 无默认值：JAR 内 ui/ 没有 .js 时抛 StopIteration（脚本直接带堆栈崩溃，而不是可读的校验失败）；同时只解码第一个 JS 分片，若构建产物拆成多个 chunk（新增文案落在其它 chunk），第 94 行的新 UI 标记校验会误判失败。建议先断言存在 .js 并拼接所有分片。
- 判断：该collector确用next选择首个.js检查新UI文案，当前单bundle可用，拆chunk后可误拒，缺js裸StopIteration也不清楚。采纳非空JS集合及所有受管chunk语义检查，不将未来分包当当前功能缺失。
- 证据：scripts/collect-m1055-evidence.py:93

## OCR-2080 · 部分采纳

- 位置：scripts/collect-m1055-evidence.py:34；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：第 34 行只要求测试报告“不晚于日志结束时间”，无法排除上一次构建遗留的旧 surefire 报告。触发条件：未清理 `harness-*/target/surefire-reports` 时本次构建未重跑某模块测试（如 -pl/增量/跳过），旧报告 mtime 仍满足条件，第 38-39 行累加后依然等于 371，证据会声称本轮已通过全部 Java 测试。建议以本次构建产物（如 JAR 的 mtime）或构建开始时间作为下界，或在验证前清理 surefire-reports。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：第 34 行只要求测试报告“不晚于日志结束时间”，无法排除上一次构建遗留的旧 surefire 报告。触发条件：未清理 `harness-*/target/surefire-reports` 时本次构建未重跑某模块测试（如 -pl
- 证据：scripts/collect-m1055-evidence.py:34

## OCR-2081 · 部分采纳

- 位置：scripts/collect-m1055-evidence.py:101；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：第 101 行只把 `frontend/src/**` 与顶层 `frontend/tests/*` 的哈希“记录”进结果，第 92 行只校验 `frontend/dist` 与 JAR 内 UI 一致，没有任何一步把 dist 绑定到当前前端源码。触发条件：构建完成后修改 `frontend/src/...`（甚至新增文件），第 40 行的 mtime 检查只覆盖 Java 源与 pom，第 92 行仍通过，最终证据声称前端源码与 JAR/UI 一致但实际不一致。建议由构建产出 dist 输入清单（或 vite 构建时写入的输入哈希文件）并在采集时校验，至少校验 dist 内文件的 mtime 不早于所有前端源码。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：第 101 行只把 `frontend/src/**` 与顶层 `frontend/tests/*` 的哈希“记录”进结果，第 92 行只校验 `frontend/dist` 与 JAR 内 UI 一致，没有任何一步把 dist
- 证据：scripts/collect-m1055-evidence.py:101

## OCR-2082 · 部分采纳

- 位置：scripts/collect-m1055-evidence.py:86；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：第 86-87 行对两个 native 实现文件分别在“任意”历史 manifest 中独立匹配哈希，无法证明同一轮三平台 CI 同时覆盖了当前两份实现：SystemCredentials.java 的哈希可来自 manifest A，NativeCredentials.java 的哈希可来自 manifest B（不同提交/不同运行）。触发条件：两次不同的历史 CI 各修改其中一个文件，拼接后仍判定通过。建议要求同一个 manifest 同时包含两份文件的当前哈希。
- 判断：原生实现文件逐个any历史manifest匹配，不能证明同一份三平台来源同时覆盖完整实现集；采纳完整native源集合+探针脚本+历史run身份一起匹配，仍明确历史专项不替代本次全量CI。
- 证据：scripts/collect-m1055-evidence.py:86

## OCR-2083 · 部分采纳

- 位置：scripts/collect-m1055-evidence.py:64；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：第 64-69 行的证据落盘发生在后续所有校验（native、历史三平台、JAR/UI、upstream）之前，且是一次性的 `write_bytes` 覆盖。触发条件：循环写完全部 M10.5.5-* 记录后，第 67/68/84/87/90/92/94/95/97/100 行任一条 require 失败——此时 docs/testing 中已混入本轮新的浏览器记录与 `M10.5.5-current-native-results.json`，而 `M10.5.5-results.json` 仍是旧内容（或不存在），仓库证据自相矛盾，且无法区分哪些文件属于同一次成功采集。建议先完成全部校验，把待写内容收集到内存，最后统一写临时文件并 `Path.replace` 原子替换。
- 判断：collector在后续native/JAR/UI/upstream全部校验之前直接覆盖部分docs/testing记录，后段失败可留下新旧混合。采纳全部验证后staging与manifest最后原子发布，失败保留明确回执，历史事实不重造。
- 证据：scripts/collect-m1055-evidence.py:64

## OCR-2084 · 部分采纳

- 位置：scripts/collect-m1057-evidence.py:52；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：浏览器检查的通过语义过宽：实际产物（如 docs/testing/M10.5.7-extensions-results.json）的 checks 元素是 `{"name": ..., "passed": true}`，而这里对任意字符串元素一律视为通过。触发条件：结果文件中把失败/跳过项以字符串形式写入（或少量失败项被替换成字符串、再补齐数量到 expected）时，`len(checks) == expected` 与 `all(...)` 仍成立，失败证据会被提升为 M10.5.7 PASS。此外非 str 且非 dict 的元素会抛 AttributeError 而非可读的校验失败。建议只接受 dict 且 `c.get('passed') is True`。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m1057-evidence.py:52

## OCR-2085 · 部分采纳

- 位置：scripts/collect-m1057-evidence.py:59；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：文件系统边界未收敛：证据 JSON 中的相对路径直接与 ROOT 拼接后读取。pathlib 对绝对路径会丢弃根（`ROOT / '/etc/passwd' == Path('/etc/passwd')`），也未拒绝 `..` 片段，因此 `image['path']` 可指向 ROOT 之外的文件，使“截图无漂移”的校验在错误目标上通过；第 61 行 `sourceSha256` 的 key、第 92 行从 `ui-files.txt` 得到的 `p`、以及第 100 行 `upstream.json` 的 `destination` 同样存在该问题（`p` 可含 `../`）。建议统一走一个 `resolve()` 后 `is_relative_to(root)` 的辅助函数，对越界路径直接 require 失败。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：文件系统边界未收敛：证据 JSON 中的相对路径直接与 ROOT 拼接后读取。pathlib 对绝对路径会丢弃根（`ROOT / '/etc/passwd' == Path('/etc/passwd')`），也未拒绝 `..` 片
- 证据：scripts/collect-m1057-evidence.py:59

## OCR-2086 · 部分采纳

- 位置：scripts/collect-m1057-evidence.py:93；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：`next(p for p in files if p.endswith('.js'))` 没有默认值，当 `ui-files.txt` 中不存在 .js（例如构建产物改为嵌套目录或仅剩压缩 chunk 命名）时会抛出裸 StopIteration，报错信息完全无法定位；同时它只取 files 中的第一个 .js，若构建拆成多个 chunk，第 94 行的“新 UI 文案存在性”检查可能落在不含该文案的 chunk 上而误判失败，或读到任意 chunk 使检查失去意义。建议使用带默认值的 next 并 require，再对全部 .js 或明确的主入口 bundle 做检查。
- 判断：该collector确用next选择首个.js检查新UI文案，当前单bundle可用，拆chunk后可误拒，缺js裸StopIteration也不清楚。采纳非空JS集合及所有受管chunk语义检查，不将未来分包当当前功能缺失。
- 证据：scripts/collect-m1057-evidence.py:93

## OCR-2087 · 部分采纳

- 位置：scripts/collect-m1057-evidence.py:30；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：证据绑定的源集合不完整：`sources` 只包含根/各模块 pom.xml 与 `harness-*/src/**/*.java`，遗漏了 `src/main/resources`、`src/test/resources`、`.properties/.yaml/.json` 资源、assembly 及其它构建描述文件。触发条件：在 m1057-final-verify5.log 之后修改 `harness-cli/src/main/resources/...` 或 `harness-*/src/test/resources/...`，第 40 行的 mtime 检查与第 68 行的 native sourceSha256 全量比对都不会发现（后者只按 `sources` 集合比对），仍会生成 status=PASS 的 M10.5.7 证据。同理 `frontend` 源文件只记录哈希、从不做 mtime 约束。建议把 `harness-*/src/**/*` 下所有文件（过滤目录）纳入 `sources`。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：证据绑定的源集合不完整：`sources` 只包含根/各模块 pom.xml 与 `harness-*/src/**/*.java`，遗漏了 `src/main/resources`、`src/test/resources`、`.
- 证据：scripts/collect-m1057-evidence.py:30

## OCR-2088 · 部分采纳

- 位置：scripts/collect-m1057-evidence.py:92；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：这里只证明了 JAR 内的 ui/ 与磁盘上的 `frontend/dist` 一致，没有任何环节证明 dist 是由当前的 `frontend/src`/`frontend/tests` 构建出来的：既没有校验 `.dsh-tmp/m1057-ui-build.log` 的 BUILD SUCCESS，也没有校验 src 的 mtime 早于 dist。触发条件：修改 `frontend/src` 后未重新 build（或 build 失败）时，dist 仍是旧产物、与 JAR 完全一致，UI 相关校验全部通过；而第 106 行 `frontendSha256` 记录的却是新源码哈希，二者在证据里没有任何关联，导致“UI 与源一致”的结论不成立。建议加入 ui-build 日志内容/mtime 与 dist 文件 mtime 的先后校验。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：这里只证明了 JAR 内的 ui/ 与磁盘上的 `frontend/dist` 一致，没有任何环节证明 dist 是由当前的 `frontend/src`/`frontend/tests` 构建出来的：既没有校验 `.dsh-tm
- 证据：scripts/collect-m1057-evidence.py:92

## OCR-2089 · 部分采纳

- 位置：scripts/collect-m1057-evidence.py:87；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：历史 native CI 溯源条件过弱：只要任意递归找到的 `sourceSha256` 字典里含有这两个文件的当前哈希即通过，没有校验 `prior.get('status') == 'PASS'`、没有要求命中来自同一次运行/同一个 manifest（`M10.5.4-native-ci-results.json` 内嵌套了多个 per-OS 的 sourceSha256），也不校验三个 OS 的结果都存在，但第 110 行仍把它记为 `historicalNativeCI` 跨平台证据。触发条件：prior 中任一 OS/任一次（甚至 FAILED 的）运行记录了匹配哈希，或三个 OS 只留其一时，仍会写出“未变更 native 的三平台证据”。建议先校验 prior 顶层 status/milestoneStatus 与三平台条目完整性，再在单个 manifest 内做全路径比对。
- 判断：逐native文件any匹配可拼不同历史manifest且未统一验证历史来源status；采纳同一三平台run内每侧完整实现集合与探针身份，并明确只证未变原生专项而非本次全量CI。
- 证据：scripts/collect-m1057-evidence.py:87

## OCR-2090 · 部分采纳

- 位置：scripts/collect-m1057-evidence.py:54；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：settings 分支完全跳过了 `status == 'PASS'` 校验，只检查了一个布尔字段。触发条件：`M10-settings-browser-results.json` 中 `status: "FAIL"`（或缺失）但 `fixtureCredentialsCleaned: true`、checks 恰好 26 项且形式合法时，该文件会被当作成功并通过第 63-64 行复制成 `M10.5.7-settings-browser-results.json`，进入 PASS 结果。`status == 'PASS'` 应对所有结果文件统一校验。
- 判断：settings仅校fixtureCredentialsCleaned，历史旧schema本来无status，不能否定旧证据；采纳版本化schema，新结果须PASS/checks/cleanup一致并拒FAILED+cleaned矛盾。
- 证据：scripts/collect-m1057-evidence.py:54

## OCR-2091 · 部分采纳

- 位置：scripts/collect-m1059-evidence.py:55；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：`isinstance(c,str)` 让任何字符串形态的 check 无条件视为通过——若上游把失败原因（例如 'FAILED: ...'）以字符串写入 checks，此处仍判定该浏览器检查通过，配合下面的 `len(r['checks'])==EXPECTED[...]` 计数即可伪造全绿。建议只接受结构化字典并用 `c.get('passed') is True`，字符串一律视为无效证据。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m1059-evidence.py:55

## OCR-2092 · 部分采纳

- 位置：scripts/collect-m1059-evidence.py:47；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：privateRoot 与 originalFilesSha256 的键来自可被前序步骤写坏/篡改的 browser-runtime.json，这里未做任何包含性校验：prepare-test-browsers.py 中的 is_relative_to(.build-cache) 检查不会在此处复现，且 `private/p` 在 p 为绝对路径时会丢弃 privateRoot（pathlib 语义），`../` 形式的键还能让脚本去哈希缓存目录外的任意文件，从而伪造「浏览器二进制未变」的结论。建议 resolve 后强制 `private.resolve().is_relative_to((ROOT/'.build-cache').resolve())` 并校验每个键 resolve 后仍以 private 为前缀。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：privateRoot 与 originalFilesSha256 的键来自可被前序步骤写坏/篡改的 browser-runtime.json，这里未做任何包含性校验：prepare-test-browsers.py 中的 is_
- 证据：scripts/collect-m1059-evidence.py:47

## OCR-2093 · 部分采纳

- 位置：scripts/collect-m1059-evidence.py:53；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：矩阵记录中的 path 直接 `ROOT/entry['path']` 拼接，绝对路径会覆盖 ROOT、`../` 可逃出仓库边界（下方 `ROOT/shot['path']`、`ROOT/name` 的 screenshot/sourceSha256 键同样如此），使校验读取并信任仓库外的文件内容。建议对每个证据路径做 resolve 后 `is_relative_to(OUT)`（截图限定在 OUT 或 frontend/tests）校验，不满足即拒绝。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：矩阵记录中的 path 直接 `ROOT/entry['path']` 拼接，绝对路径会覆盖 ROOT、`../` 可逃出仓库边界（下方 `ROOT/shot['path']`、`ROOT/name` 的 screenshot/s
- 证据：scripts/collect-m1059-evidence.py:53

## OCR-2094 · 采纳

- 位置：scripts/collect-m1059-evidence.py:31；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：三条前端客户端断言用的都是非锚定 re.search：`pass\s+33\b` 会在 `bypass 33 ...`、`cleanup pass 33 ...` 等任意出现位置命中；同时 client.log 既没有正向前缀约束也没有 mtime 新鲜度校验（对比 surefire 报告做了 mtime 检查），因此旧的或部分失败后残留的日志都能通过。建议锚定行首（如 `^\s*(?![0-9])...` / `re.M` 配合整行匹配）并校验 client.log 的生成时间。
- 判断：该collector用re.search在整份客户端文本匹配分散pass/fail/skipped，未解析单轮完整摘要/退出码/构建身份；确可命中陈旧或拼接结果。改用结构化测试结果并绑定本次runId，不能只加一个mtime下界。对应主张：三条前端客户端断言用的都是非锚定 re.search：`pass\s+33\b` 会在 `bypass 33 ...`、`cleanup pass 33 ...` 等任意出现位置命中；同时 clien
- 证据：scripts/collect-m1059-evidence.py:31

## OCR-2095 · 采纳

- 位置：scripts/collect-m1059-evidence.py:23；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：证据门禁只做子串匹配，且校验日志本身没有新鲜度校验。若 verification.log 是被截断的旧日志、或来自只构建了部分模块的运行（例如仅在某个聚合模块中打印过 BUILD SUCCESS），此处仍会通过；后续对 Java 源码只做了「源码 mtime <= 日志 mtime」这一单向比较，jar/前端产物与日志的构建归属也没有绑定。建议改用锚定正则（如 `^\[INFO\] BUILD SUCCESS$`，re.M）并要求日志 mtime 晚于 jar 与前端构建产物。
- 判断：该collector用re.search在整份客户端文本匹配分散pass/fail/skipped，未解析单轮完整摘要/退出码/构建身份；确可命中陈旧或拼接结果。改用结构化测试结果并绑定本次runId，不能只加一个mtime下界。对应主张：证据门禁只做子串匹配，且校验日志本身没有新鲜度校验。若 verification.log 是被截断的旧日志、或来自只构建了部分模块的运行（例如仅在某个聚合模块中打印过 BUILD SUCCESS），此
- 证据：scripts/collect-m1059-evidence.py:23

## OCR-2096 · 部分采纳

- 位置：scripts/collect-m1059-evidence.py:32；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：把「文件为空」当作 SDK 类型检查成功：如果 typecheck 步骤根本没执行而只是被 `: > sdk-typecheck.log` 或 CI 中途失败留下空文件，此项直接通过；反之若报告文件不存在会抛 FileNotFoundError 而不是给出可定位的校验失败。建议要求日志非空且包含明确的成功标记（如 tsc 的退出码记录/`success` 行），并同样校验 mtime 不早于源码。
- 判断：空typecheck日志只说明无输出，不单独证明命令执行；CI本有set-e非零门禁，采纳记录命令退出码/runId/工具版本与检查输入，不因空日志否定已有CI步骤。
- 证据：scripts/collect-m1059-evidence.py:32

## OCR-2097 · 不采纳

- 位置：scripts/collect-m1059-evidence.py:43；OCR原严重度：high
- 计划：无
- 原意见：Firefox 不可用证据被硬编码为 Windows 专用错误码 14001。probe-firefox.py 在非 Windows 上用 `getattr(error,'winerror',None)` 记录，因此键值为 None：在 Linux/macOS 上本地运行（engines 为 {'chromium','webkit'}，会进入本分支）时此断言必然失败，除非伪造 Windows 证据；若日志字段缺失则直接 KeyError。建议按当前平台校验对应字段，并校验 status=='UNAVAILABLE' 的同时要求存在可读的 reason/错误码，而不是固定 14001。
- 判断：Firefox未在引擎集时只允许本机Windows14001是显式例外；CI必须三个引擎，Linux/macOS不可用不得变通过。扩展接受任意OSError会削弱门禁。
- 证据：scripts/collect-m1059-evidence.py:43

## OCR-2098 · 采纳

- 位置：scripts/collect-m1059-evidence.py:46；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：runtime 状态只处理了 PRIVATE_ASSEMBLY_MANIFEST 分支：prepare-test-browsers.py 还会写 DEFAULT_RUNTIME，任何其它/损坏/被篡改的 status 值都会静默跳过全部浏览器二进制完整性校验并继续产出 RUNNER_PASS。建议先校验 status 属于 {'DEFAULT_RUNTIME','PRIVATE_ASSEMBLY_MANIFEST'} 并对未知值报错，同时在该分支内要求 originalFilesSha256 非空（当前空字典会让 `all(...)` 真空通过）。
- 判断：runtime只对PRIVATE_ASSEMBLY_MANIFEST分支校验，其余任意status绕过；应明确枚举DEFAULT_RUNTIME/PRIVATE并拒未知类型，校验各分支所需字段。
- 证据：scripts/collect-m1059-evidence.py:46

## OCR-2099 · 部分采纳

- 位置：scripts/collect-m1059-evidence.py:87；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：候选产物只按 sdk_bom 中列出的条目逐一读取校验，没有像校验 CLI jar 那样做「精确条目集合」比对：zip 中多出的条目、被替换的 launcher jar/启动脚本等都不会被发现，release 包内容因此不受证据保护。建议先断言非目录条目集合等于期望集合（再把其中 sdk 源码逐个比对哈希），发现多余条目即失败。
- 判断：collect只核SDK条目而candidate-smoke另查manifest；仍可加强最终候选精确集合和ZIP/JAR/UI身份绑定，不能只凭此认整个发行包完全不受保护。
- 证据：scripts/collect-m1059-evidence.py:87

## OCR-2100 · 不采纳

- 位置：scripts/collect-m1059-evidence.py:92；OCR原严重度：medium
- 计划：无
- 原意见：workingTreeDirty 只被记录进报告，却完全不参与 status 判定：在未提交/被本地修改的工作树上跑出的证据仍会输出 RUNNER_PASS 或 LOCAL_PASS_CI_PENDING，报告表面上像是可复现的通过结论。建议在 status 计算前要求 working_tree 干净（或把状态降级为 DIRTY_WORKTREE_PENDING 并置 m11Eligible=False），保持与 m11Eligible=False 的语义一致。
- 判断：本地未提交代码真实验证允许LOCAL_*_CI_PENDING并记录dirty，最终aggregate明确workingTreeDirty is False才准M11。不能把本地准备通过冒当正式CI。
- 证据：scripts/collect-m1059-evidence.py:92

## OCR-2101 · 部分采纳

- 位置：scripts/collect-m1056-evidence.py:52；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：L52 的校验过弱：`isinstance(c, str)` 允许 checks 里直接写字符串，这种条目没有任何 passed 标记也会被计为通过；同理 `c.get('passed')` 只做真值判断，`"passed": "false"` 这样的字符串同样为真。另外若某条目是 null/数字，会抛 AttributeError 而不是可读的 `Incomplete browser checks` 失败。建议要求每条都是 dict 且 `passed is True`。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m1056-evidence.py:52

## OCR-2102 · 部分采纳

- 位置：scripts/collect-m1056-evidence.py:29；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：L29 只用全文件子串匹配 'BUILD SUCCESS'，且没有排除 'BUILD FAILURE'。触发条件：日志以追加方式写入（`>>`、`tee -a`）或复用上一次构建的日志时，只要历史上成功过一次，即使最后一次 verify 失败也照样通过这道闸门；同时日志没有绑定任何运行标识/时间区间，旧日志同样可满足。建议改为对整个日志中构建摘要的唯一性校验（例如要求末尾不含 'BUILD FAILURE' 且构建摘要出现次数为 1），并与 JAR 的 mtime 区间交叉校验。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：L29 只用全文件子串匹配 'BUILD SUCCESS'，且没有排除 'BUILD FAILURE'。触发条件：日志以追加方式写入（`>>`、`tee -a`）或复用上一次构建的日志时，只要历史上成功过一次，即使最后一次 ver
- 证据：scripts/collect-m1056-evidence.py:29

## OCR-2103 · 部分采纳

- 位置：scripts/collect-m1056-evidence.py:34；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：L34 的时间戳方向只拒绝了“比日志新”的报告，比日志旧的陈旧报告一律接受。触发条件：最终 verify 未重新执行某个模块的测试（`-pl`/`-DskipTests`/增量构建），该模块 target 下上一次运行的 TEST-*.xml 仍然满足 `mtime <= log.mtime` 并被计入硬编码的 388 总数，于是“未重新跑过的测试”被当作本轮证据。应与构建开始时间比较，例如 `max(p.stat().st_mtime for p in sources) <= file.stat().st_mtime <= log.stat().st_mtime`（可把 build_start 提到循环外只算一次）。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：L34 的时间戳方向只拒绝了“比日志新”的报告，比日志旧的陈旧报告一律接受。触发条件：最终 verify 未重新执行某个模块的测试（`-pl`/`-DskipTests`/增量构建），该模块 target 下上一次运行的 TEST
- 证据：scripts/collect-m1056-evidence.py:34

## OCR-2104 · 部分采纳

- 位置：scripts/collect-m1056-evidence.py:30；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：L30 的源码清单只覆盖 `pom.xml` 与 `harness-*/src/**/*.java`，遗漏了会改变运行结果的构建/资源输入：`harness-*/src/main/resources`（如 `.properties`、SQL、UI 之外的资源）、`.mvn`/JDK 参数、以及 `frontend/package.json`、`frontend/scripts/package-ui.mjs` 等前端构建入口。触发条件：只修改这些文件后重跑 verify，清单哈希完全不变，但结果 JSON 仍以 `sourceSha256` 宣称绑定“final local source”。建议把这些输入（含 `scripts/test-native-credentials.py`，见 L68）一起纳入清单，或收窄结果中的声明。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：L30 的源码清单只覆盖 `pom.xml` 与 `harness-*/src/**/*.java`，遗漏了会改变运行结果的构建/资源输入：`harness-*/src/main/resources`（如 `.properties
- 证据：scripts/collect-m1056-evidence.py:30

## OCR-2105 · 部分采纳

- 位置：scripts/collect-m1056-evidence.py:92；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：L92 只比对 `frontend/dist` 与 JAR 内 UI 的字节一致性，没有校验 dist 是在当前 `frontend/src` 之后构建的；而 `harness-web/pom.xml` 只是把 `frontend/dist` 当静态资源打包，Maven 并不会重新构建前端（需手工跑 `frontend/scripts/package-ui.mjs`）。触发条件：修改 `frontend/src` 后不重新构建 dist 直接跑 verify 与采集脚本，JAR 里打的仍是旧 dist，两侧哈希相同故通过，而 L105-L106 却把新的前端源码哈希记入 `frontendSha256`，形成“源码新、UI 旧”的最终证据（L94 的新字符串检查只覆盖四处文案，不能替代源码绑定）。建议要求 dist 文件 mtime 不早于最新前端源码 mtime，或把前端构建日志/构建输入一并纳入校验。
- 判断：collector只证dist与JAR字节相同，前端源码哈希是采集时记录而非构建时输入；当前Maven不自动build前端。采纳npm build的输入/输出同run摘要绑定，保留旧里程碑原证据。
- 证据：scripts/collect-m1056-evidence.py:92

## OCR-2106 · 采纳

- 位置：scripts/collect-m1056-evidence.py:41；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：L41-L44 没有任何校验把 JAR 与客户端日志绑定到本轮构建：既未要求 `jar.mtime >= max(sources.mtime)`（源文件改完但未重新打包时，`source <= log` 仍成立，陈旧 JAR 会被接受），也未要求 `client.log.mtime >= jar.mtime`。触发条件：修改 Java 源码后仅刷新 verify 日志 / 用旧 JAR 跑客户端回归，`pass 27 / fail 0` 的正则计数一样满足；浏览器记录虽用 `jarSha256` 锁定 JAR，但客户端证据没有任何 JAR 归属。建议补充 mtime 区间校验并把 `client` 日志 sha 与 jar 时间关联。
- 判断：该collector用re.search在整份客户端文本匹配分散pass/fail/skipped，未解析单轮完整摘要/退出码/构建身份；确可命中陈旧或拼接结果。改用结构化测试结果并绑定本次runId，不能只加一个mtime下界。对应主张：L41-L44 没有任何校验把 JAR 与客户端日志绑定到本轮构建：既未要求 `jar.mtime >= max(sources.mtime)`（源文件改完但未重新打包时，`source <= log
- 证据：scripts/collect-m1056-evidence.py:41

## OCR-2107 · 部分采纳

- 位置：scripts/collect-m1056-evidence.py:87；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：L87 对每个文件单独做 `any(...)`，会把不同历史 manifest 的摘要拼起来：例如 `SystemCredentials.java` 命中某次 Windows 运行的清单，而 `NativeCredentials.java` 命中另一次 Linux 运行的清单，两者组合即通过“自平台 CI 以来未变更”的判定，而这并不对应任何一次真实的三平台取证。触发条件：历史上存在多次 `M10.5.4-native-ci-results.json` 中的 manifest（或文件被选择性回滚）。建议改为按“单个 manifest 同时包含全部路径”判定。
- 判断：逐native文件any匹配可拼不同历史manifest且未统一验证历史来源status；采纳同一三平台run内每侧完整实现集合与探针身份，并明确只证未变原生专项而非本次全量CI。
- 证据：scripts/collect-m1056-evidence.py:87

## OCR-2108 · 部分采纳

- 位置：scripts/collect-m1056-evidence.py:87；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：更正上一条评论的 suggestion（此前误写为 `pass`，会删掉校验，请勿采用）。应把逐文件 `any(...)` 换成“同一份历史 manifest 覆盖全部 native_paths”的判定，并可将该判定移出 `for name in native_paths:` 循环：
- 判断：原生实现文件逐个any历史manifest匹配，不能证明同一份三平台来源同时覆盖完整实现集；采纳完整native源集合+探针脚本+历史run身份一起匹配，仍明确历史专项不替代本次全量CI。
- 证据：scripts/collect-m1056-evidence.py:87

## OCR-2109 · 部分采纳

- 位置：scripts/collect-m1056-evidence.py:64；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：L64 在循环内就把缓存记录写入 `docs/testing`，而后续的原生校验（L66-L69）、JAR/UI 校验（L88-L97）、upstream 校验（L98-L99）都还没执行。触发条件：任一后续 require 失败时，`docs/testing` 已被部分覆盖（前几个里程碑记录变成 M10.5.6 副本），却没有 `M10.5.6-results.json`，仓库里留下半更新、跨里程碑混用的证据。建议先全部校验并把内容读入内存/临时目录，最后一次性提交写入。
- 判断：collector在后续native/JAR/UI/upstream全部校验之前直接覆盖部分docs/testing记录，后段失败可留下新旧混合。采纳全部验证后staging与manifest最后原子发布，失败保留明确回执，历史事实不重造。
- 证据：scripts/collect-m1056-evidence.py:64

## OCR-2110 · 不采纳

- 位置：scripts/collect-m1056-evidence.py:68；OCR原严重度：medium
- 计划：无
- 原意见：L68 只校验了 Java/pom 清单，未校验记录中的 `probeScriptSha256`。触发条件：`scripts/test-native-credentials.py`（决定探针用例、期望值与 source 清单本身）在原生取证之后被修改，本地原生证据仍被判为有效，且 `currentNative` 里也没有记录该脚本哈希，事后无法复核。建议同时要求 `native['probeScriptSha256'] == sha(ROOT / 'scripts/test-native-credentials.py')` 并写入结果。
- 判断：这是M10.5.6历史collector，主清单已哈希fixture脚本并绑定同JAR/Java来源；当前M10.5.9 inputs覆盖scripts。没有证明原生探针被改后错用于最终结果，不追溯给旧阶段collector加新schema。
- 证据：scripts/collect-m1056-evidence.py:68

## OCR-2111 · 部分采纳

- 位置：scripts/probe-firefox.py:18；OCR原严重度：medium
- 计划：R0-02 / P3
- 原意见：`write_text` 是就地截断写：进程中断（超时被杀、Ctrl-C）或两个探测并发运行时会留下截断的或交错写入的 JSON，下游读取会得到半截文件或解析错误，属于典型的“非原子产物”缺陷。建议写入同目录临时文件后用 `os.replace` 原子替换，并对并发写入做串行化（如独占锁文件）。
- 判断：probe-firefox原地write_text有截断窗口；下游JSON损坏会拒绝，非静默PASS。可用临时文件原子替换并记录run身份。
- 证据：scripts/probe-firefox.py:18

## OCR-2112 · 不采纳

- 位置：scripts/probe-firefox.py:13；OCR原严重度：medium
- 计划：无
- 原意见：两处子进程调用（第 10 行 node、第 13 行 firefox 可执行文件）都没有传 `env=`，因此完整继承父进程环境。在 CI 中这会把全部凭据/令牌暴露给被测二进制和 Node，与该仓库的环境变量白名单要求冲突。探测只需要 `PATH`/`SystemRoot` 等最小集合，建议显式构造白名单 env 传入。
- 判断：浏览器/平台/候选冒烟启动受信任构件继承环境不是工具白名单边界，夹具覆写专用合成key；无已证实真实凭据被使用。若需隔离可显式env，但不能认现有继承即安全漏洞。
- 证据：scripts/probe-firefox.py:13

## OCR-2113 · 采纳

- 位置：scripts/probe-firefox.py:15；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：这里只捕获 `OSError`，但 `subprocess.run(timeout=10)` 超时抛出的是 `subprocess.TimeoutExpired`（继承 `SubprocessError`，不是 `OSError`），`text=True` 在 Windows 上按控制台代码页解码失败时抛的是 `UnicodeDecodeError`。两者都会逃出 try，导致前面辛苦算出的 `executableSha256` 与状态一起丢失、`availability.json` 不被写入——恰好是该脚本最需要记录的“启动未完成”场景。建议显式区分超时与其他失败，并保证无论哪条路径都落盘。
- 判断：probe仅捕OSError，TimeoutExpired/Node路径解析失败等发生在报告写入前，留旧availability。采纳全部前置/探测异常形成明确失败记录及有界调用，不把启动失败计浏览器验收通过。
- 证据：scripts/probe-firefox.py:15

## OCR-2114 · 采纳

- 位置：scripts/probe-firefox.py:10；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：可执行文件解析链路完全没有异常保护（第 9-11 行）：`check_output` 未设置 `timeout`，Node 缺失会抛 `FileNotFoundError`，Playwright 导入失败/路径不存在会抛 `CalledProcessError`（`json.loads` 拿到的是 stderr 之外的空输出，或根本不是 JSON），`json.loads` 会抛 `ValueError`，`path.read_bytes()` 抛 `OSError`。任一情况都会让脚本在写出 `availability.json` 之前直接崩溃，使这次「记录可用性」的探测丢失全部结果（下游只能看到文件缺失，无法区分“环境未装”和“探测脚本自身失败”）。建议把解析 + 哈希整体纳入 try，并对 node 调用加超时，失败时按 `UNAVAILABLE` 落盘。
- 判断：probe仅捕OSError，TimeoutExpired/Node路径解析失败等发生在报告写入前，留旧availability。采纳全部前置/探测异常形成明确失败记录及有界调用，不把启动失败计浏览器验收通过。
- 证据：scripts/probe-firefox.py:10

## OCR-2115 · 部分采纳

- 位置：scripts/frontend-sbom.py:42；OCR原严重度：high
- 计划：R6-01 / P3
- 原意见：`item['integrity']` 与第 49 行的 `item['resolved']` 都是直接下标取值，没有处理缺失情况。触发条件：生产依赖中一旦加入 git/github 依赖、`file:`/本地 tarball 依赖或 bundled 依赖，lockfile 条目通常没有 `integrity`（或 `resolved` 形如 `file:../x`），脚本会以 KeyError 崩溃而不是产出 BOM，且 `resolved` 为相对路径时写进 `externalReferences.url` 也不是合法 URI。建议改为 `.get()` 并在缺失时给出明确错误/跳过该字段（例如 `'externalReferences': [...] if item.get('resolved') else []`），同时 `item['version']` 也建议一并做存在性校验。
- 判断：生成器明确拒绝链接且要求SHA512/license，只支持受审npm registry lock条目；缺metadata应清晰拒绝而非建议跳过integrity掩盖来源。采纳缺version/integrity/resolved诊断和受支持来源说明，非必须支持git/file依赖。
- 证据：scripts/frontend-sbom.py:42

## OCR-2116 · 采纳

- 位置：scripts/frontend-sbom.py:26；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：bom-ref 只由 name@version 组成，忽略了安装路径。lockfile 里同一 name@version 完全可能出现在多个路径（如 `node_modules/x` 与 `node_modules/a/node_modules/x`，嵌套/hoisting 差异），此时 `components` 会输出两个相同 bom-ref 的组件、`dependencies` 也会出现两条同 ref 的条目且各自 dependsOn 可能不同，CycloneDX 要求 bom-ref 在文档内唯一，图上会出现歧义甚至无法通过校验。建议：要么在 bom-ref 中带上安装路径（如用 path 后缀/qualifier 保证唯一），要么以 ref 为 key 对 components 与 dependencies 去重并合并 dependsOn。
- 判断：ref只由npm name@version生成，runtime按安装路径逐项输出，同版本双安装路径会重复bom-ref且边可不同。应按实例唯一ref或明确合并组件/边并校验ref唯一；当前6依赖没有重复不否定生成器处理合法lock的缺陷。
- 证据：scripts/frontend-sbom.py:26

## OCR-2117 · 部分采纳

- 位置：scripts/frontend-sbom.py:51；OCR原严重度：high
- 计划：R6-01 / P2
- 原意见：这里把 optionalDependencies 和 peerDependencies 合并成“必需边”，随后第 52 行对每条边调用 `resolve()`，缺失即抛 ValueError。触发条件：任一运行时（非 dev）包声明了未安装的 optional peer（如 vite 的 sass/less/stylus/sugarss/tsx，见 package-lock.json 第 1972-1984 行）或被 os/cpu 条件排除、未写入 lockfile 的可选依赖，整个 BOM 生成就会直接失败；即使不失败，把 peer 需求当作安装边输出也会让依赖图包含实际不存在的边。建议：对 `optionalDependencies` 与 `peerDependenciesMeta[dep].optional == true` 的边，解析失败时跳过而不是报错，peer 需求本身建议不写入 dependsOn（或单独标记）。同时建议用 `resolve(path, dep)` 返回 None 的方式替代抛异常，由调用方决定是否必需。
- 判断：edges无条件合并optionalDependencies与所有peers后resolve缺失即抛错，合法未安装optional会被错误拒绝。采纳区分必需/可选并核对实际安装边；原例vite是dev已被runtime过滤，不证明当前锁文件因此构建失败。
- 证据：scripts/frontend-sbom.py:51

## OCR-2118 · 采纳

- 位置：scripts/frontend-sbom.py:54；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：应用根节点只连接了 `packages['']['dependencies']`，没有包含根包自身声明的 `optionalDependencies`（也忽略了根级 peer）。触发条件：frontend/package.json 增加一个 optional 生产依赖后，它会被纳入 `runtime` 并作为 component 输出，但根节点没有指向它，BOM 中该组件成为不可达的孤立节点，“运行时依赖闭包”的断言与实际图不符。另外 `packages['']['dependencies']` 是直接下标，根包没有 dependencies 字段时会 KeyError。建议：`root_edges = {**packages[''].get('dependencies', {}), **packages[''].get('optionalDependencies', {})}` 后再解析。
- 判断：根只连接dependencies而runtime包含非dev optional，新增根optional后component可成为孤儿；将实际安装的根optional纳入根边并验证可达性。不能要求未安装可选组件必有条目。
- 证据：scripts/frontend-sbom.py:54

## OCR-2119 · 采纳

- 位置：scripts/frontend-sbom.py:47；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：npm 的 `license` 字段是任意字符串，直接写进 CycloneDX 的 `license.id` 会被当作 SPDX ID。触发条件：某个运行时包声明了 SPDX 表达式（如 `(MIT OR Apache-2.0)`）、`SEE LICENSE IN LICENSE`、URL 或自定义许可证文本，生成的 BOM 将不符合 CycloneDX 1.6 schema（`license.id` 需为合法 SPDX ID），下游消费方可能拒绝或误判许可证。建议：对照 SPDX ID 列表校验，命中才用 `{'id': ...}`，否则降级为 `{'name': item['license']}`（复合表达式可用 `licenses` 顶层的 `expression`）。
- 判断：license.id直接接受任意license字符串，表达式/SEE LICENSE文本会被错标为单SPDX标识。按已知ID、SPDX expression或name分类并校验BOM schema；保留原许可文本不猜归类。
- 证据：scripts/frontend-sbom.py:47

## OCR-2120 · 不采纳

- 位置：scripts/run-m1059-platform.py:11；OCR原严重度：medium
- 计划：无
- 原意见：第 11 行把 `os.environ` 整体注入每个子进程（native / sdk / examples / browser，以及 node/Playwright 派生的进程），CI 中的凭据服务令牌、OAuth/AI provider key 等都会被这几个 stage 无条件继承，且没有白名单收敛。触发条件：CI 环境里同时存在 `JHARNESS_*` 凭据或其它服务 token 时，任何被测代码/浏览器测试都能读取它们，扩大了凭据暴露面。建议按白名单显式挑选（PATH、JAVA_HOME、JHARNESS_PYTHON、JHARNESS_FIREFOX_EXECUTABLE、必要的代理/临时目录变量），其余不继承。
- 判断：浏览器/平台/候选冒烟启动受信任构件继承环境不是工具白名单边界，夹具覆写专用合成key；无已证实真实凭据被使用。若需隔离可显式env，但不能认现有继承即安全漏洞。
- 证据：scripts/run-m1059-platform.py:11

## OCR-2121 · 部分采纳

- 位置：scripts/run-m1059-platform.py:22；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：第 22 行 `subprocess.run(..., timeout=1800)` 的 `subprocess.TimeoutExpired` 没有被捕获：超时时会抛出未处理异常，既不打印 `name` 对应的失败摘要（第 23 行的 SystemExit 路径不会走到），也无法说明是哪个 stage 超时；同时 `subprocess.run` 超时只会 kill 直接子进程，node/Playwright 派生的浏览器与下载子进程会继续存活并占住 `.build-cache` 与端口，使后续任务产生脏状态。建议显式捕获 TimeoutExpired，用可识别的消息终止（例如 `raise SystemExit(f'{name} timed out after 1800s; see ...') from None`），并在超时后清理进程组（POSIX 用 `start_new_session=True` + `os.killpg`，Windows 用 Job Object 或 `taskkill /T`）。
- 判断：platform stage timeout未结构化捕获且subprocess只杀直接子；采纳记录失败stage/日志、回收该stage自有进程树，避免残留锁。不能声称OS不可中断进程一定可强杀。
- 证据：scripts/run-m1059-platform.py:22

## OCR-2122 · 不采纳

- 位置：scripts/run-m1059-platform.py:14；OCR原严重度：medium
- 计划：无
- 原意见：第 14 行在跑任何 stage 之前就无条件执行 `prepare.prepare()`，而它内部会调用 `node`（读 frontend/node_modules/playwright）并按需复制浏览器运行时。只要 `frontend/node_modules` 未安装、node 不可用或复制/校验抛错（CalledProcessError / OSError / ValueError），整个矩阵会在 native、sdk、examples 之前直接中断，导致这些完全不依赖浏览器的 stage 失去结果。触发条件：Windows 上 playwright 未安装或 SXS manifest 处理失败。建议把浏览器准备放到 browser stage 之前惰性执行，或至少用 try/except 捕获后明确降级（不设置 JHARNESS_FIREFOX_EXECUTABLE 继续），并输出可诊断的错误信息。
- 判断：平台矩阵前prepare浏览器是当前命令前置，缺node_modules应明确失败，提前失败不等于错误执行SDK。按stage选择是新增调试能力。
- 证据：scripts/run-m1059-platform.py:14

## OCR-2123 · 部分采纳

- 位置：scripts/run-m1059-platform.py:21；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 21-22 行把子进程 stdout/stderr 原样落盘到 `.build-cache/M10.5.9/<name>.log`，没有任何脱敏。触发条件：sdk/browser/launcher 测试在 verbose 或失败路径下打印 Authorization 头、token、cookie 或 PII，这些内容会持久化进构建缓存（通常还会被当作 CI 工件上传）。建议在写日志前对常见敏感模式（Bearer/Basic、`token|secret|password|authorization` 键值、长 base64）做掩码，至少对浏览器/凭据相关 stage 生效。另外该文件以 'w' 覆盖写，若某 stage 未执行，目录里上一次运行的旧日志会残留并被误读为本次结果，建议运行开始时清理或写带 run id 的子目录。
- 判断：platform将子输出原样落日志，测试失败可能带fixture值；采纳所有已知合成凭据统一脱敏和日志预算，未证实真实宿主秘密已经泄漏。
- 证据：scripts/run-m1059-platform.py:21

## OCR-2124 · 部分采纳

- 位置：scripts/scan-osv.py:79；OCR原严重度：low
- 计划：R6-02 / P2
- 原意见：第 79 行 `scan(*sys.argv[1:])` 未做参数个数校验：缺参数或多传参数会抛 TypeError，脚本以 traceback 退出且不写报告；同时 `scan()` 内部第 25-32 行任何异常都会向上冒泡，使第 81 行的退出码契约（PASS=0，其余=1）形同虚设。建议显式校验 `len(sys.argv) == 3`，不满足时打印用法并 `sys.exit(2)`；若 `scan()` 抛异常则转换为 INCOMPLETE 语义的退出码，保证 CI 失败信息可读。
- 判断：scan-osv的BOM解析/参数错误在report try外会非零且不覆盖旧报告；采纳参数schema错误也输出INCOMPLETE失败记录并失效旧PASS。并非网络失败会静默PASS。
- 证据：scripts/scan-osv.py:79

## OCR-2125 · 采纳

- 位置：scripts/scan-osv.py:28；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：第 28 行用 `assert` 校验外部输入（BOM 文件内容），在 `python -O`/`PYTHONOPTIMIZE` 下断言被剥离，非 CycloneDX 的 JSON 会被当作 BOM 继续扫描并可能产出 `status: PASS` 的报告（脚本头注释宣称 fail closed）。此外该断言与下面第 29-32 行的解析/空集合检查都位于 `try` 之外、`report` 创建之前：BOM 缺失、非法 JSON、`bomFormat` 不符或 `components` 为空时 `scan()` 直接抛异常返回，`target/osv-results.json` 根本不会生成，`build-candidate.py`/`verify-m9-evidence.py` 等下游会因文件缺失报 FileNotFoundError，而非看到约定的 `INCOMPLETE`。建议改为显式 `if bom.get("bomFormat") != "CycloneDX": raise ValueError(...)`，并把 BOM 读取/解析/校验一并纳入 `try`，使所有失败都落盘为 INCOMPLETE 报告。
- 判断：bomFormat用assert运行时校验，-O剥离后非CycloneDX但同字段JSON可扫描并PASS。换显式require并加优化模式负例。
- 证据：scripts/scan-osv.py:28

## OCR-2126 · 部分采纳

- 位置：scripts/scan-osv.py:29；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：BOM validation failures happen outside the try block and before the report is created: direct subscripts (`c["group"]`, `c["version"]`) raise KeyError/TypeError, and the 'no external components' ValueError aborts too — in all cases `osv-results.json` is never written, so downstream sees FileNotFoundError instead of the agreed INCOMPLETE/PASS status. Move parsing/validation inside the try and treat 'no external dependencies' as a normal PASS terminal state.
- 判断：scan-osv的BOM解析/参数错误在report try外会非零且不覆盖旧报告；采纳参数schema错误也输出INCOMPLETE失败记录并失效旧PASS。并非网络失败会静默PASS。
- 证据：scripts/scan-osv.py:29

## OCR-2127 · 不采纳

- 位置：scripts/scan-osv.py:48；OCR原严重度：medium
- 计划：无
- 原意见：第 48 行一次性把所有待查查询放进同一个 `querybatch` 请求，但 OSV 该接口单次最多 1000 条查询；聚合 SBOM 的外部组件超过 1000 个时服务端返回 400，扫描将永久停在 `INCOMPLETE`（且异常信息只有 HTTPError，看不出是批次超限）。建议按 ≤1000 分块发送，并把分块索引与 `pending` 的 `index` 对齐，避免位置映射错乱。
- 判断：当前18个Maven外部依赖远低于1000，上千组件不在当前清单；超限API报错保持INCOMPLETE且非零不误放行。可未来扩容再分批。
- 证据：scripts/scan-osv.py:48

## OCR-2128 · 不采纳

- 位置：scripts/scan-osv.py:68；OCR原严重度：medium
- 计划：无
- 原意见：第 68 行只用 `withdrawn` 决定 `status`/`activeFindings`，但第 62 行已把全部 ID（含已撤回）写入 `report["packages"][*].vulnerabilities`。结果会出现 `status: PASS` 却仍列出若干漏洞 ID 的报告，审计方/下游消费者无法判断这些 ID 是否已撤回，容易误读为"漏报"或"状态不一致"。建议在包维度区分撤回项（例如另存 `withdrawn` 列表或对每条标注 `withdrawn`），或从 `vulnerabilities` 中剔除已撤回 ID，保持报告自洽。
- 判断：记录原始OSV命中包含withdrawn同时activeFindings排除撤回是有意保留审计来源，PASS表示零未撤回发现，文档已明确；删除撤回历史反损失证据。
- 证据：scripts/scan-osv.py:68

## OCR-2129 · 部分采纳

- 位置：scripts/scan-osv.py:63；OCR原严重度：low
- 计划：R6-02 / P2
- 原意见：第 63-67 行对每个唯一 ID 串行发起一次 `vulns/{id}` 请求，既没有并发也没有数量/响应体大小上限：发现数上千时扫描耗时为 O(n)×网络往返，且 `json.load(response)` 会无界读取服务端响应（无 `Content-Length` 上限校验），不符合"有界数据处理"要求。建议对 ID 数量或总记录体积设上限并按上限快速失败，或改为受控并发；同时可对抓取失败的单个 ID 记录为显式错误而非整体中断（当前行为虽 fail-closed，但会把已成功抓取的记录全部丢弃）。
- 判断：OSV响应json.load无字节上限且vuln详情串行无总deadline；采纳字节/记录/总体时间预算，失败仍INCOMPLETE，当前18包无已发生资源耗尽证据。
- 证据：scripts/scan-osv.py:63

## OCR-2130 · 部分采纳

- 位置：scripts/prepare-test-browsers.py:44；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：对"链接运行时"的校验可被绕过，无法保证私有副本的边界： 1. `Path.is_symlink()` 对 Windows 目录 junction（IO_REPARSE_TAG_MOUNT_POINT）等 reparse point 返回 False，而 `shutil.copytree(source,target,dirs_exist_ok=True)` 默认 `symlinks=False` 会跟随 junction/link 复制其真实内容，因此 junction 形式的链接运行时不会被 line 45 拦截，`copied` 哈希校验也会全部通过。 2. `source.rglob('*')` 只遍历 `source` 的子项，`source` 自身是符号链接/junction 时不会被检测。 3. 该校验在 line 41 `(source/'mozglue.dll').read_bytes()` 之后才执行，读取动作已经跟随过链接。 建议：在读取任何文件之前先校验根路径（`source.is_symlink()`/`Path.is_junction()`，或低版本用 `os.lstat(source).st_reparse_tag != 0`），并在遍历时用 `os.lstat`/`entry.is_symlink() or is_junction()` 拦截每个条目的 reparse point；也可显式 `copytree(..., symlinks=True)` 后拒绝任何链接条目。
- 判断：prepare仅is_symlink，Windows junction/reparse及源根别名需更完整检测；采纳真实源/目标边界与常规文件/目录校验。只复制专用缓存，不修改全局安装。
- 证据：scripts/prepare-test-browsers.py:44

## OCR-2131 · 不采纳

- 位置：scripts/prepare-test-browsers.py:42；OCR原严重度：medium
- 计划：无
- 原意见：这个边界校验是死代码，拦不住任何情况：`tempfile.mkdtemp(dir=ROOT/'.build-cache')` 必然返回该目录下的路径，而两侧都经过 `resolve()`，`target.is_relative_to((ROOT/'.build-cache').resolve())` 恒为 True（且目录此时已被创建）。真正未受约束的是 `source`：它来自 Playwright/Node 的输出，既未校验位于 `frontend/node_modules` 之下，也未校验不在 `.build-cache` 内。若 `source` 解析到 `.build-cache` 中上一次生成的 `m1059-firefox-*`，脚本会把已注入 `mozglue.manifest` 的私有运行时再整树复制一次，并在新报告里以 `originalFilesSha256`/`originalFilesUnchanged=True` 把它当成官方原始文件登记（缓存内递归复制与体积膨胀，且报告失真）。建议在 `mkdtemp` 之前校验 `source.resolve()` 位于预期的 Playwright 浏览器目录内、且不位于 `.build-cache` 之下。
- 判断：mkdtemp后真实根包含性是防御断言，当前不可达不表示缺陷；删除不会提高正确性，不能以保护冗余要求改代码。
- 证据：scripts/prepare-test-browsers.py:42

## OCR-2132 · 部分采纳

- 位置：scripts/prepare-test-browsers.py:54；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：报告中两项完整性结论并未真正被验证，构不成可复现的证明： 1. `copied` 是 `copytree` 之后才从 `source` 重新读取计算的，只覆盖 `is_file()` 的常规文件；源在复制期间被改动时，只会得到一个在复制完成后才产生的期望值，空目录/非常规条目则完全不在校验范围内。 2. line 41 从 `source/mozglue.dll` 抽取 manifest 发生在复制之前，随后哈希并写入私有副本的 `mozglue.dll` 可能是另一份字节，两者不做比对，注入的 `<file name="mozglue.dll">` 与实际副本不一致时只会以启动失败的形式暴露。 3. `originalFilesUnchanged=True` 是硬编码常量：写入 `mozglue.manifest` 并用私有副本二次执行 `--version` 之后，从未复查 `source` 原文件，下游校验方会无条件信任该字段。建议复制前先对 `source` 计算哈希，注入后再复核 `source` 未变化，并用比较结果写入该字段（无法确认时写 UNKNOWN 而非 True），同时比对 `mozglue.dll` 在复制前后的哈希。
- 判断：copied摘要在copy后读取source，能证明目标等于该后读源但不能证明源从未变；采纳复制前后源清单一致、目标精确集合与明确措辞，不把originalFilesUnchanged常量当完整证明。
- 证据：scripts/prepare-test-browsers.py:54

## OCR-2133 · 部分采纳

- 位置：scripts/prepare-test-browsers.py:46；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：缺少失败清理与回滚：从 line 42 的 `mkdtemp` 到最终写 `browser-runtime.json` 之间，任何一步失败（`copytree` 中途出错、line 48 哈希不匹配、line 49 `/mozglue.manifest` 已存在、line 52 私有副本 `--version` 失败）都不会删除 `target`，完整 Firefox 副本（数百 MB、数万文件）会滞留在 `.build-cache` 且没有任何报告引用它；重复运行持续产生新的 `m1059-firefox-*` 目录，既无复用也无清理。此外失败路径不会重写/失效已有的 `browser-runtime.json`，上一次运行留下的 `PRIVATE_ASSEMBLY_MANIFEST`/`privateRoot` 会被后续校验方当作本次结果读取（陈旧状态被重放）。建议用 try/except 包裹复制→注入→启动流程：失败时 `shutil.rmtree(target, ignore_errors=True)`，并先写入状态为 PENDING/UNKNOWN 的报告、成功后再更新，同时在启动阶段清理未被报告引用的历史私有目录。
- 判断：prepare中途失败临时私有目录残留且旧runtime报告未失效；采纳失败收据/有界清理自己创建的目录。诊断保留可选，但不能误当成功运行时。
- 证据：scripts/prepare-test-browsers.py:46

## OCR-2134 · 部分采纳

- 位置：scripts/prepare-legal.py:43；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：`archive.read(filename)` / `jar.read_bytes()` materialize whole entries in memory with no size cap (and the JAR is read a second time just to hash it), so a zip bomb or oversized license blob in a third-party JAR can exhaust memory. Stream in chunks (or use `hashlib.file_digest`) with an enforced maximum size, and check the resolved destination is inside `output` before writing.
- 判断：prepare-legal对本地BOM坐标/JAR/POM条目缺统一路径和大小预算；采纳坐标语法/真实根、压缩项限额、先验证再输出。依赖源受管，非已证明远程利用。
- 证据：scripts/prepare-legal.py:43

## OCR-2135 · 部分采纳

- 位置：scripts/prepare-legal.py:30；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：Maven coordinates from `bom.json` are joined directly into filesystem paths for both the read base (`repo`) and the write target (`output/artifacts`). The zip-entry guard below (line 41) only protects entry names, not the coordinate itself: a component whose `name`/`version` is `../../x` makes `target.mkdir(parents=True)` and `dest.write_bytes(...)` create files outside `third-party/` (the failure from `dest.relative_to(output)` only happens *after* the write). Validate each coordinate segment before building paths.
- 判断：prepare-legal对本地BOM坐标/JAR/POM条目缺统一路径和大小预算；采纳坐标语法/真实根、压缩项限额、先验证再输出。依赖源受管，非已证明远程利用。
- 证据：scripts/prepare-legal.py:30

## OCR-2136 · 不采纳

- 位置：scripts/prepare-legal.py:49；OCR原严重度：medium
- 计划：无
- 原意见：`xml.etree.ElementTree` is documented as not safe against maliciously crafted input (entity expansion / quadratic blowup). Parsing an arbitrary third-party POM with no size limit or DTD rejection allows a DoS or malformed-XML abort of the whole build. Use `defusedxml.ElementTree` (or reject `<!DOCTYPE`/`<!ENTITY`) and cap the POM size before parsing.
- 判断：本机Python3.12/3.14的stdlib ElementTree已有现代实体扩展限制，不加载外部实体；泛称ET不安全不足证明当前受管POM能触发攻击。仍可在输入预算中限制POM大小，不盲添运行依赖。
- 证据：scripts/prepare-legal.py:49

## OCR-2137 · 不采纳

- 位置：scripts/prepare-legal.py:50；OCR原严重度：medium
- 计划：无
- 原意见：Only the hardcoded `http://maven.apache.org/POM/4.0.0` namespace is matched. POMs served without that namespace (older/repackaged artifacts) yield `declaredLicenses: []`, and since the findall result is empty the script can then wrongly abort at line 57 with "Missing legal material" even though the POM does declare licenses. Match elements name-agnostically (e.g. iterate `model.iter()` and compare `tag.rsplit('}', 1)[-1]`).
- 判断：当前实际Maven中央POM均采用标准namespace且遇无法解析license会失败，非静默放行；无namespace repackaged依赖是未来支持范围。
- 证据：scripts/prepare-legal.py:50

## OCR-2138 · 部分采纳

- 位置：scripts/prepare-legal.py:56；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：The supplemental mapping is keyed by artifact name only, so unrelated artifacts sharing a name (e.g. `annotations` covering org.jetbrains:annotations vs. another group's `annotations`, or a `pty4j` fork) inherit the wrong pinned files, and multi-module variants such as `kotlin-stdlib-jdk8` get no entry at all and abort the build with "Missing legal material". Key the mapping by `group:name` (or by the full coordinate) instead of `name`.
- 判断：supplemental按artifact名匹配而非完整group:name，当前库存固定可用但同名不同group会误配。采纳完整坐标键和不允许未知项默用补充来源。
- 证据：scripts/prepare-legal.py:56

## OCR-2139 · 部分采纳

- 位置：scripts/sdk-inventory.py:24；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见："仅标准库" 校验可被绕过：这里只拦截 func 为裸 `ast.Name` 且名为 `__import__`/`eval`/`exec` 的调用。诸如 `importlib.import_module('requests')`、`builtins.__import__('x')`、`getattr(builtins,'__import__')('x')`、`getattr(builtins,'exec')(...)` 以及 `compile(...)` 都会通过 AST 检查；同时 `from builtins import __import__` 后再调用也不会命中。结果是报告里 `externalRuntimeDependencyCount=0` 的结论可以被一个真实的第三方运行时依赖悄悄违反。建议改为基于属性/名称白名单判定：对 `ast.Call` 同时检查 `ast.Attribute`（attr 属于 `{__import__, import_module, eval, exec, compile}`）、对 `ast.Name` 也加入 `compile`，并拒绝引用 `builtins`/`importlib` 的危险成员（或在导入集合中直接禁止 `importlib`）。
- 判断：AST只拒裸Name动态导入，importlib/builtins等可绕过标准库声明检查；这是源码模式门禁不是恶意Python沙箱。采纳明确支持静态进口策略/拒已知动态形式并声明限制，不能声称能证明任意Python无隐藏执行。
- 证据：scripts/sdk-inventory.py:24

## OCR-2140 · 采纳

- 位置：scripts/sdk-inventory.py:29；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：TypeScript 侧只匹配带 `from` 的说明符，副作用式导入 `import 'some-package'`（无 from 子句、ESM 中最常见的纯副作用引入）完全不会被 `re.findall` 捕获，因此非 node: 的运行时依赖可以绕过检查；`export * from 'x'` 之外的 `import x = require(...)` 也只靠后面的 require 正则兜底。建议补一条针对 `^\s*import\s*['"]([^'"]+)` 的匹配（或在同一正则里改为 `(?:from\s+|^\s*import\s*)['"]([^'"]+)`），并把两部分说明符合并后再校验 `node:` 前缀。
- 判断：TS只提取from说明符，side-effect import 'x'不进modules且不触import(正则，确漏运行依赖。补语法级静态导入/导出扫描与负例。
- 证据：scripts/sdk-inventory.py:29

## OCR-2141 · 部分采纳

- 位置：scripts/sdk-inventory.py:33；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：SBOM 组件版本 `'1.0.0'` 是硬编码的，而 `sdk/typescript/package.json` 中已有 `"version": "1.0.0"`（同一个脚本已解析该目录下的 package.json）。一旦发布版本号从 package.json 升级而这里忘记同步，生成的 SBOM 会静默上报错误的组件版本，依赖该 BOM 的许可证/制品核对随之失效。建议在循环里保存 `sdk/typescript/package.json` 的 `version`（缺省时显式报错），在此处引用该值；`protocolVersion` 也应同样从 SDK 声明处读取或至少与之一致。
- 判断：SDK BOM版本写死当前与package相同，未来升级易漂移；采纳读取唯一版本并检查两份package一致，不是当前版本错误。
- 证据：scripts/sdk-inventory.py:33

## OCR-2142 · 部分采纳

- 位置：scripts/sdk-inventory.py:19；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：Python 导入扫描只解析 `sdk/python/jharness_sdk.py`，但 `FILES` 明确把 `sdk/examples/run.py`、`sdk/examples/loopback_provider.py` 也纳入了源码清单——这两个也是会随 SDK 发布/执行的运行时脚本，若它们引入第三方包，脚本仍会输出 "0 external runtime dependencies"。同理 `FILES` 是硬编码清单，新增的 sdk/ 源文件（例如 `sdk/tests/*` 之外的新模块）不会被哈希也不会被扫描，清单会静默过期。建议对 `FILES` 中所有 `.py` 逐个做 AST 导入校验（或至少遍历 `sdk/**/*.py` 中参与发布的部分），并在文件缺失/列表为空时显式失败。
- 判断：Python依赖检查只覆盖库主文件，而候选分发examples也执行；采纳所有分发执行源静态导入检查并明确README非可执行不扫描。
- 证据：scripts/sdk-inventory.py:19

## OCR-2143 · 部分采纳

- 位置：scripts/scan-source-secrets.py:25；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：文件系统边界未校验：`git ls-files` 会返回符号链接，`ROOT/name` 未做 `resolve()`/`relative_to(ROOT)` 校验就直接读取，带白名单后缀的软链接可指向仓库外的文件（如 `/etc/...`）甚至 `/dev/zero`，使扫描器读取/泄露仓库外内容或无限读取。建议解析真实路径并确认仍位于 ROOT 之内，同时只接受常规文件；对越界项记录到报告而非静默读取。
- 判断：git ls-files可含软链，read_bytes跟随目标且只按后缀筛选；采纳拒链接/非普通文件及真实根检查，输出仅path/line本不会直接泄漏匹配秘密原文。
- 证据：scripts/scan-source-secrets.py:25

## OCR-2144 · 采纳

- 位置：scripts/scan-source-secrets.py:27；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：有界读取失效且异常未受控：`read_bytes()` 先把整个文件读进内存，16 MiB 的检查在其后执行，任何被跟踪或未跟踪（`--others` 包含工作区新文件/构建产物）的大文件都会在检查前耗尽内存；同时 `read_bytes()` 对已删除但被跟踪的文件、坏符号链接会抛 `FileNotFoundError`/`OSError`，导致整个扫描中断且不产出 `target/source-secrets.json`，在 CI 上表现为无报告。建议先用 `stat().st_size`/`is_file()` 判断再读，并把单文件读取异常捕获后记录为该文件的错误条目，继续扫描其余文件。
- 判断：16MiB上限在read_bytes后才判断，无法限制读取峰值；删除tracked文件也可在报告前抛错留旧PASS。应流式限读/先stat并受控错误回执。
- 证据：scripts/scan-source-secrets.py:27

## OCR-2145 · 部分采纳

- 位置：scripts/scan-source-secrets.py:21；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：`git ls-files -z` 输出的是原始路径字节，严格 `decode('utf-8')` 遇到非 UTF-8 文件名会抛 `UnicodeDecodeError`，直接终止整个门禁且不生成报告（`check_output` 失败同理）。建议使用 `errors='surrogateescape'` 保留原始字节语义，避免因单个异常文件名导致全量扫描失败。
- 判断：git原始路径decode UTF8对POSIX非UTF8名可失败，当前仓库名UTF8有效；采纳filesystem解码/显式拒绝并输出失败记录，不把decode异常算通过。
- 证据：scripts/scan-source-secrets.py:21

## OCR-2146 · 部分采纳

- 位置：scripts/scan-source-secrets.py:18；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：行号计算为每个匹配都执行一次 `data[:m.start()]` 切片拷贝，复杂度为 O(文件大小 × 匹配数)：一个 16 MiB 的压缩/生成文件若命中数百个模式，会产生数百份最多 16 MiB 的临时拷贝，造成明显内存与 CPU 抖动。建议用不产生拷贝的 `data.count(b'\n',0,m.start())`，或先按换行切分建立行偏移表后二分查找。
- 判断：每匹配data[:start]计行复制前缀，重复命中多时二次成本；采纳count start/end或一次换行索引，当前文件上限有限但仍可减少峰值。
- 证据：scripts/scan-source-secrets.py:18

## OCR-2147 · 不采纳

- 位置：scripts/test-m1059-gates.py:6；OCR原严重度：low
- 计划：无
- 原意见：模块加载未校验 spec / spec.loader 是否为 None：spec_from_file_location 在路径无已知后缀或不合法时会返回 spec 但 loader 为 None（或直接返回 None），此处会抛出 'NoneType' object has no attribute 'exec_module' 这类难以定位的错误，两个被测模块还可能被错加载后在所有用例里静默失效。建议 fail-fast 并给出明确错误信息。
- 判断：加载的是with_name固定存在.py源码，同仓库测试需要该模块；路径变更/文件缺失应测试收集失败，未校spec不是错加载后所有用例静默失效的证据。
- 证据：scripts/test-m1059-gates.py:6

## OCR-2148 · 采纳

- 位置：scripts/test-m1059-gates.py:24；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：拒绝类断言只校验异常类型 ValueError，而不校验异常来源。aggregate() 在“不是恰好三份报告”“OS 缺失”“矩阵不完整”等多处都抛 ValueError，因此一旦 fixture 被意外改坏（例如 milestone 写错导致报告被 rglob 过滤掉），或某条校验被删除后由另一条不相干的校验先抛错，这些用例依旧为绿，真正要守护的闸门条件形同未测。建议用 assertRaisesRegex 绑定具体错误消息（如 'Different CI/source/UI: sourceSha256'、'Not actual clean passing CI'、'Incomplete current matrix'），确保命中预期分支。
- 判断：当前负例仅assertRaises(ValueError)，aggregate多道门均同类型；fixture坏到其它门时会掩盖目标门被删。使用特定诊断/错误码匹配并保留正例，必要时mutation确认目标门确被覆盖。
- 证据：scripts/test-m1059-gates.py:24

## OCR-2149 · 采纳

- 位置：scripts/test-m1059-gates.py:29；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：当前只覆盖了 status、sourceSha256、browserChecks、ciRunId 四个拒绝条件，而 aggregate() 中 paidRequests!=0、workingTreeDirty 为真、java.failures/errors/skipped 非零、java.tests/sdkChecks/frontendClientChecks 数量不符、uiSha256 跨 OS 不一致、commit 不一致、suites 用例集合不一致等关键闸门条件均无用例。这些正是“本地脏树/付费请求/未跑满矩阵却获得 M11 资格”的绕过路径，属于必须守护的数据完整性条件，建议逐条补充负向用例。
- 判断：聚合器有paid/dirty、Java各计数、UI/commit/case集合条件，现测试只覆盖4种拒绝；这些是M11实际准入不变量，补一字段一负例和跨OS差异反例有独立价值。
- 证据：scripts/test-m1059-gates.py:29

## OCR-2150 · 部分采纳

- 位置：scripts/test-m1059-gates.py:36；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：路径穿越只覆盖了 POSIX 风格的 '../outside.dll'。manifest() 的校验依赖属性字典完全相等，当前实现尚可挡住的还有 Windows 反斜杠 '..\\outside.dll'、绝对路径（'C:\\Windows\\x.dll'、'/etc/passwd'）、以及大小写/编码变体；清单在 Windows 上被消费，'\.\.\\' 变体才是最贴近实际的攻击形态。建议补齐这些变体，避免将来校验放宽成前缀/后缀判断时测试仍然通过。
- 判断：manifest要求file属性字典严格等于{name:mozglue.dll}，当前各种路径都已拒绝。采纳低成本Windows路径/绝对路径边界反例以守护这个明确安全契约，不声称当前存在穿越。
- 证据：scripts/test-m1059-gates.py:36

## OCR-2151 · 部分采纳

- 位置：scripts/test-m1059-gates.py:34；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：缺少“blob 中不含清单”的基线用例：当 data 里没有 b'<assembly xmlns=' 时，manifest() 走的是 bytes.index 抛出的 ValueError('substring not found')，与“显式拒绝”无法区分；一旦有人把提取逻辑改成“缺失时返回 b''”，未嵌入清单的二进制会被静默放行，而现有用例不会失败。建议显式断言缺失清单时抛出带明确语义的 ValueError（或返回 None 且不被当作合法清单）。
- 判断：缺assembly目前bytes.index会抛ValueError，fail-closed；采纳缺清单二进制负例及明确错误，不将异常与显式拒绝的诊断差异夸成现已放行。
- 证据：scripts/test-m1059-gates.py:34

## OCR-2152 · 部分采纳

- 位置：scripts/collect-m1058-evidence.py:51；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：校验条件过宽，会把失败的检查当作通过：`isinstance(c, str)` 无条件接受任意字符串（例如 `"FAILED"`、`"false"`），而 `c.get('passed')` 只做真值判断，`{"passed": "false"}`、`{"passed": 1}` 等非布尔真值也会通过。触发条件：任一 fixture 的 checks 里出现字符串项或非布尔 passed 字段时，该 fixture 仍被复制为验收证据。当前所有 fixture 的 checks 都是 `{name, passed}` 且 passed 为布尔，建议显式要求布尔真值并拒绝未知类型。另外 `expected is None` 这个分支永远不会成立（fixtures 列表里所有期望值都是整数），属于死代码，可一并移除。
- 判断：该collector确接受isinstance(c,str)或passed真值，缺少对象检查的严格布尔约束；旧记录允许字符串型成功名称是既有格式，不能简单宣布所有历史字符串无效。采纳按fixture schema验证checks数组、唯一名称与passed is True；历史快照保留，新门禁负例含FAIL字符串/字符串false。
- 证据：scripts/collect-m1058-evidence.py:51

## OCR-2153 · 部分采纳

- 位置：scripts/collect-m1058-evidence.py:56；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：fixture 提供的路径被直接与 ROOT 拼接，缺少仓库边界校验：`ROOT / path` 在 path 为绝对路径时会丢弃 ROOT（Path 语义），含 `..` 的相对路径也会上跳，随后 `sha()` 会读取并摘要仓库外的任意文件；第 55 行 `image['path']` 与第 77 行用 JAR 内 `ui/ui-files.txt` 条目拼接 `ROOT/'frontend/dist'` 存在同样问题。触发条件：fixture/构建产物中的路径被篡改或包含 `../`、绝对路径时，证据校验会跨越文件系统边界读取到仓库外内容而不会报错。建议增加统一的解析函数（`path = (ROOT / str(rel).replace(chr(92), '/')).resolve(); require(path.is_relative_to(ROOT.resolve()), ...)`），三处路径拼接都走它。
- 判断：源码直接将本地证据元数据与ROOT/资源目录拼接，未统一拒绝绝对路径、上跳及链接；采纳限定每种元数据的根/格式并先校验再哈希。证据来自本地受控采集，不能据此等同远程文件泄漏；命令行显式外部输入另按用途允许。具体字段主张：fixture 提供的路径被直接与 ROOT 拼接，缺少仓库边界校验：`ROOT / path` 在 path 为绝对路径时会丢弃 ROOT（Path 语义），含 `..` 的相对路径也会上跳，随后 `sha()` 会读取并摘要仓
- 证据：scripts/collect-m1058-evidence.py:56

## OCR-2154 · 部分采纳

- 位置：scripts/collect-m1058-evidence.py:35；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：源码与 surefire 报告的新鲜度只依赖 mtime，无法证明内容未被修改：第 35 行仅比较 `p.stat().st_mtime <= log.stat().st_mtime`，先改 Java 源码再用 `touch -d`/复制保留旧时间戳即可绕过（第 30 行的测试报告同样只检查了上界，没有下界，旧构建遗留的报告只要文件名匹配且合计 410 仍会通过）；而第 68/85 行记录的 `sourceSha256` 只写进结果文件，从未与构建时快照（JAR 内清单或构建日志中的摘要）比对。触发条件：在验证之后修改源码并保留旧 mtime，或在 target 目录残留上一轮报告。建议在构建阶段输出源码摘要清单并与采集时比对，或至少要求报告 mtime 不早于最新源码 mtime。
- 判断：核对该collector后，记录当前哈希/单向mtime确不等于构建开始时输入与完整报告集的身份；采纳运行清单含输入摘要、完整测试case集合、命令/退出码、JAR/UI/日志同run绑定。此结论不证明历史验收实际造假，旧快照不重写。具体缺口：源码与 surefire 报告的新鲜度只依赖 mtime，无法证明内容未被修改：第 35 行仅比较 `p.stat().st_mtime <= log.stat().st_mtime`，先改 Java 源码再用 `touch -d
- 证据：scripts/collect-m1058-evidence.py:35

## OCR-2155 · 部分采纳

- 位置：scripts/collect-m1058-evidence.py:59；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：校验与落盘交错，失败时会留下半更新的证据集：每个 fixture 校验完就立即写入 docs/testing，若后面的 fixture（或第 62-79 行的 SDK/native/JAR 校验）失败，docs/testing 里已出现本轮的记录文件，而索引 M10.5.8-results.json（第 93 行）仍是上一轮的旧版本，二者的 records/jarSha256 不一致，后续人工或脚本会把这份混合证据当作有效快照提交。建议先完成全部校验并在内存/临时目录中暂存所有输出，全部通过后再统一替换（写临时文件 + `os.replace` 原子改名），最后再写索引文件。
- 判断：源码在全部校验完成前逐份覆盖docs/testing或原地写最终JSON，后段失败可留半更新证据；采纳先验证/同一字节快照再staging与manifest最后原子发布，失败保留明确状态。历史已归档内容不重造。
- 证据：scripts/collect-m1058-evidence.py:59

## OCR-2156 · 部分采纳

- 位置：scripts/collect-m1058-evidence.py:53；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：settings 分支绕过了 PASS 状态校验：当 `name.startswith('M10-settings')` 时只检查 `fixtureCredentialsCleaned`，从不校验 `status`。该标志由 frontend/tests/settings.mjs 在写文件时被硬编码为 true（`JSON.stringify({ ...result, fixtureCredentialsCleaned: true })`），因此 M10-settings-browser-results.json 的通过状态实际上完全没有被验证；只要 checks 数量和 JAR 哈希对上，一个 `status: "FAIL"` 的设置页采集结果也会被改名为 M10.5.8-settings-browser-results.json 写进 docs/testing 作为验收证据。建议 settings 分支同时要求 `status == 'PASS'`。
- 判断：settings分支仅要求fixtureCredentialsCleaned而非所有新schema的显式PASS；历史settings文件原本用cleanup标志、不能凭缺status否定旧验收。采纳新版本统一status/checks/cleanup全部验证及拒绝矛盾FAILED+cleaned。
- 证据：scripts/collect-m1058-evidence.py:53

## OCR-2157 · 不采纳

- 位置：scripts/collect-m1058-evidence.py:80；OCR原严重度：low
- 计划：无
- 原意见：`frontend/tests` 的 glob 缺少 `is_file()` 过滤：一旦该目录下出现子目录（例如新增 fixtures/ 或 helpers/ 目录），目录项会进入 frontend 列表，随后第 86 行构造 `frontendSha256` 时调用 `sha(p)`/`read_bytes()` 直接抛 IsADirectoryError，收集中断；即使不中断也会把目录混进哈希映射。建议与 src 分支保持一致，加上 `if p.is_file()`。
- 判断：M10.5.8特定旧collector在tests目录只有文件时有效，目录新增会失败而非假通过；当前新collector已用p.is_file过滤递归。无需为了未来目录重组修历史采集脚本。
- 证据：scripts/collect-m1058-evidence.py:80

## OCR-2158 · 不采纳

- 位置：scripts/test-source-secrets.py:5；OCR原严重度：low
- 计划：无
- 原意见：直接链式解引用 `spec.loader.exec_module(...)` 缺少对 `spec`/`spec.loader` 为 None 的防御，也未把模块注册进 `sys.modules`。若被加载脚本改名或路径变化，测试会在收集阶段抛出难以定位的 AttributeError（甚至 traceback 指向本文件的这一行而非真实原因）；模块未登记也会让 `dataclasses`/`typing` 或相对导入在后续扩展该脚本时失败。建议显式校验并注册模块。
- 判断：固定.py模块加载不需动态后缀猜测，scanner没有dataclass/相对import。假想今后改名或新增dataclass不是当前失败/静默未测，不添加推测性导入保护。
- 证据：scripts/test-source-secrets.py:5

## OCR-2159 · 部分采纳

- 位置：scripts/test-source-secrets.py:10；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：脱敏断言强度不足，可能掩盖真实泄漏。1) 只把 `str(result)` 与完整明文比较，因此前缀/截断泄漏（例如 findings 返回 `value[:8]` 或包含匹配片段的 context 字段）仍会通过；2) 只检查 `result[0]`，未断言 `len(result)==1`，若某条规则在库中变更导致同一输入产生多条命中，后续元素的泄漏或错误行号不会被发现；3) `value.decode()` 在将来 findings 返回字节片段时还可能抛 UnicodeDecodeError，使测试以异常而非断言失败告终。建议先断言命中数量与字段集合，再对全部元素做脱敏检查。
- 判断：findings当前只返回pattern/line，不返回任何match值；原断言不会漏掉现行真实输出。采纳按接口约定校验所有元素只含这两键及预期数量，防今后增加泄密context，去掉不相干decode假想。
- 证据：scripts/test-source-secrets.py:10；scripts/scan-source-secrets.py:19

## OCR-2160 · 不采纳

- 位置：scripts/test-source-secrets.py:13；OCR原严重度：low
- 计划：无
- 原意见：这条负向断言把两条独立规则（环境变量引用白名单、短合成串长度阈值）混在一个 `assertEqual([])` 里：任一规则回归，甚至新增的误报，都会表现为同一条失败，无法定位是哪条规则失效。建议拆成两个独立用例/断言，使白名单行为可被单独验证。
- 判断：一个固定输入无命中是可理解的同一负例，任何误报均会失败；拆成两个test仅改善诊断，未增强关键断言，不独立立修复阶段。
- 证据：scripts/test-source-secrets.py:13

## OCR-2161 · 部分采纳

- 位置：scripts/test-native-credentials.py:16；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：环境与进程启动失败未被处理：`os.environ["JAVA_HOME"]` 在未设置时抛 `KeyError`；JAVA_HOME 指向不存在目录、jar 未构建时抛 `FileNotFoundError`；探针超过 180s 抛 `subprocess.TimeoutExpired`。这些异常都会在任何报告写入/输出之前中断脚本，导致测试以无结构的 traceback 失败，且 `.build-cache` 中不会留下失败证据（脚本本应保证“无论结果如何都产出可审计报告”）。建议用 `os.environ.get` 校验 JAVA_HOME、检查 java/jar 是否存在，并捕获 `OSError`、`subprocess.TimeoutExpired`，统一生成 `expectationMet=False` 的报告后以非零码退出。
- 判断：test-native前置/Timeout异常与JSON非object未形成结构化失败报告，可能留旧文件；采纳输入shape/启动失败/超时回执与旧PASS失效，保持不包含秘密。
- 证据：scripts/test-native-credentials.py:16

## OCR-2162 · 不采纳

- 位置：scripts/test-native-credentials.py:18；OCR原严重度：high
- 计划：无
- 原意见：`--case` 只影响期望判定和输出文件名，未传递给探针进程（`CredentialStoreProbe` 的 `main` 也不解析任何参数），因此 `credentials`、`missing-service`、`locked-service` 三个场景运行的是完全相同的探针流程。missing-service / locked-service 场景无法真正复现“服务缺失/服务锁定”状态，产生的证据会被错误标记为对应场景。建议把 case 作为参数传入探针（探针侧据此切换场景），或在脚本中显式拒绝探针不支持的多场景运行。
- 判断：case由workflow先移除DBUS、再启动服务、再Lock创建三种真实环境；同一Java探针测试相同操作恰能验证环境差异。不需传case改变探针逻辑来伪造缺失/锁定。
- 证据：scripts/test-native-credentials.py:18

## OCR-2163 · 部分采纳

- 位置：scripts/test-native-credentials.py:20；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：只捕获了 JSON 解析错误，未校验解析结果的结构：若探针输出合法 JSON 但不是对象（如数组、字符串、`null`），后续 `report.get(...)` 会抛 `AttributeError`/`TypeError` 直接崩溃，`report.update(...)` 也会失败；若 `checks` 字段类型异常，`report.get('checks') == {'cleanup': False}` 会被静默判为期望不满足而非结构错误。建议解析后增加 `isinstance(report, dict)` 校验，并对 `checks` 做类型检查。
- 判断：test-native前置/Timeout异常与JSON非object未形成结构化失败报告，可能留旧文件；采纳输入shape/启动失败/超时回执与旧PASS失效，保持不包含秘密。
- 证据：scripts/test-native-credentials.py:21

## OCR-2164 · 不采纳

- 位置：scripts/test-native-credentials.py:25；OCR原严重度：medium
- 计划：无
- 原意见：对 `checks` 使用整体字典相等判定过于严格：探针在 `require(vault.supported())` 之后失败（如 missing-service / locked-service 场景下 store 可用但读写失败）时会先写入 `available: true`，此时 `checks` 为 `{'available': True, 'cleanup': False}`，即使确实处于 unavailable 状态也会被判为 `expectationMet=False`；反之探针新增任何检查项都会让该场景永久失败。建议仅校验关键键值（`report.get('checks', {}).get('cleanup') is False`）并对 `cleanup` 键缺失单独处理。
- 判断：expectedUnavailable严格{'cleanup':False}是要排除supported后真实读写失败，不应把available:true后任意失败当预期不可用；放宽会掩盖真实持久化缺陷。
- 证据：scripts/test-native-credentials.py:25

## OCR-2165 · 不采纳

- 位置：scripts/test-native-credentials.py:39；OCR原严重度：medium
- 计划：无
- 原意见：报告输出缺少允许列表/脱敏边界：脚本把探针返回的整个 `report` 原样 `print` 并持久化到 `.build-cache`，只排除了 `sourceSha256`。这与模块文档“never emits secrets”的承诺不符——脱敏完全依赖当前探针字段恰好安全，一旦探针新增诊断字段（原生错误文本、store 描述、目标路径等）就可能把凭据相关内容写入构建产物和 CI 日志。建议按固定字段白名单构造对外报告（如 platform/credentialStore/passed/failureCode/checks 及脚本自身生成的哈希与时间戳），并让 `report.update` 不覆盖/不转发未知字段。
- 判断：CredentialStoreProbe输出本身为受管安全schema，脚本原样保留实际检查是来源证据；假定未来新增secret字段不证明当前泄漏。可保持schema审查，不无由删实际诊断。
- 证据：scripts/test-native-credentials.py:39

## OCR-2166 · 不采纳

- 位置：scripts/test-release-tools.py:15；OCR原严重度：medium
- 计划：无
- 原意见：测试把临时目录建在仓库根目录 `<repo>/target` 下，且 `target` 目录本身在本测试结束后不会被删除：临时子目录被 `TemporaryDirectory` 清理了，但 `target/` 会被永久留在工作区。触发条件：`target/` 尚不存在时运行该测试。后果：(1) 在只读检出的仓库（如挂载为只读的 CI 工作区）上 `mkdir` 抛 `OSError: [Errno 13] Permission denied`，测试直接报错；(2) 测试污染源码工作区，若与其他并行任务/脚本共用 `target/` 还可能产生相互干扰（例如被构建清理任务并发删除导致 `TemporaryDirectory` 创建失败）。`scanner.scan()` 只接收任意路径，无需依赖仓库布局，建议直接使用系统临时目录。
- 判断：测试在target下用TemporaryDirectory且清理子目录，target父目录保留是正常构建缓存；当前测试不承诺只读checkout可写产物，无需改为产品缺陷。
- 证据：scripts/test-release-tools.py:15

## OCR-2167 · 部分采纳

- 位置：scripts/test-release-tools.py:31；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：mock 按 `len(calls)`（已发生调用次数）而不是按请求体内容分派响应，属于隐式的顺序/状态依赖：(1) 只要 `scan()` 的请求序列发生任何变化（例如新增一次预检请求、querybatch 被拆成两次调用），第 1 次调用就会被错误地当成首批查询返回分页夹具，断言可能以“看似通过”的方式掩盖分页/明细查询缺陷；(2) 该函数内有 `self.assertEqual(...)`，一旦断言失败会在 `scan()` 的 `except Exception` 中被吞掉并转成 `status=INCOMPLETE`，测试最终只报 `'INCOMPLETE' != 'FINDINGS'`，丢失真实的断言信息，排障成本高；(3) `path.startswith("vulns/")` 分支对任意漏洞 ID 都返回同一个 record，因此 `scan()` 的 record ID 校验从未被真正验证。建议按 `body["queries"][0]["package"]["name"]`/`page_token` 内容分派，并把断言移到 `run_scan()` 返回后对 `calls` 列表做。
- 判断：按调用次数的OSV替身刻意验证分页顺序，当前可用；补请求path/body显式断言能更早诊断协议变动，不能称当前必有假阳性。
- 证据：scripts/test-release-tools.py:31

## OCR-2168 · 部分采纳

- 位置：scripts/test-sdk-inventory.py:24；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：probe 只断言异常类型，且没有正控制。`ValueError` 的子类包括 `json.JSONDecodeError` 和 `UnicodeDecodeError`，因此若临时副本里的 `sdk/package.json` 解析失败或某个源文件解码异常，`build(root)` 也会抛出 `ValueError`，probe 会在完全没有触发目标依赖检测逻辑的情况下通过（例如 package.json 用例只写入了 `{"dependencies":...}`，未来 build 增加必填字段校验后会变成因错误原因通过）。建议先对未修改的副本执行一次 `inventory.build(root)` 断言成功作为基线，再用 `assertRaisesRegex(ValueError, 'Unexpected SDK runtime dependency|Nonstandard Python import|Nonstandard or dynamic TypeScript runtime import')` 精确匹配目标错误信息。
- 判断：库存负例只断ValueError易误接其他失败，正例只校数量不能证明精确keys/hash；采纳明确错误码/消息和正控制、实际字节集合摘要断言。
- 证据：scripts/test-sdk-inventory.py:24

## OCR-2169 · 部分采纳

- 位置：scripts/test-sdk-inventory.py:13；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：完整性断言过弱：`sourceSha256` 只校验条目数且把 9 硬编码（与 `inventory.FILES` 长度重复，FILES 变更时易失配），没有断言 key 集合等于 `inventory.FILES`，也没有用文件实际字节重新计算 SHA-256 与报告值比较，`licenseSha256` 与 SBOM 中的 `Apache-2.0` 声明也完全未被校验（与测试名 "sources and license are complete" 不符）。因此当 digest 实现退化为返回常量或 key 名写错时该测试仍会通过。另外 `externalRuntimeDependencyCount` 在 `build()` 中是常量 0，该断言恒真；`pythonImports`/`typescriptImports` 也从未被检查是否仅含标准库/`node:` 模块。建议：`expected={p: hashlib.sha256((inventory.ROOT/p).read_bytes()).hexdigest() for p in inventory.FILES}`，断言 `assertEqual(expected, report['sourceSha256'])`、`assertEqual(len(inventory.FILES), len(report['sourceSha256']))`，并校验 `licenseSha256`、`set(report['pythonImports']) <= sys.stdlib_module_names`。
- 判断：库存负例只断ValueError易误接其他失败，正例只校数量不能证明精确keys/hash；采纳明确错误码/消息和正控制、实际字节集合摘要断言。
- 证据：scripts/test-sdk-inventory.py:13

## OCR-2170 · 采纳

- 位置：scripts/live-evaluate.py:41；OCR原严重度：high
- 计划：R1-07 / P1
- 原意见：触发条件：模型写的 `example.Calculator` 带静态初始化块，或 oracle 反射调用的方法内读取环境。这里启动的 JVM 继承脚本的完整环境（含 `DEEPSEEK_API_KEY`），又加载并初始化模型编译出的类，因此被评估代码可执行 `System.getenv("DEEPSEEK_API_KEY")`（harness 对 execute_command 有 LocalProcessRunner.buildEnvironment 白名单，但此处的 java 进程没有），并把值打印到 stdout；该 stdout 被原样放进 `independentOracle.stdout`，最终写入 ledger.json 与 `docs/testing/M4-live-results.json`，造成凭据落盘。建议给 oracle 的 javac/java 传同一个最小环境白名单，并且不要把被测代码可控的原始 stdout 持久化（截断/仅保留 PASS 标记）。
- 判断：oracle的javac/java未传env，加载并初始化模型生成Calculator类；父进程含真实DEEPSEEK_API_KEY，stdout又原样进入independentOracle。隔离oracle环境且扫描/限制输出，不能只信模型代码子串黑名单。
- 证据：scripts/live-evaluate.py:41

## OCR-2171 · 部分采纳

- 位置：scripts/live-evaluate.py:140；OCR原严重度：high
- 计划：R1-07 / P1
- 原意见：触发条件：会话日志缺失、首行之后被截断（第 143 行的 json.loads 遇到残行会直接抛 JSONDecodeError，但 `log.exists()` 为真的空文件/只有头的文件不会），或该次 run 走了不同 session 名。此时 `budget` 为空，`upper` 被记为 0，即使 `model.requested` 事件已发生（真实已产生付费请求）也会把本次运行写成 0 成本；而本次 directory 会立刻进入 `recorded`，后续 main 里的丢失成本恢复扫描会永久跳过它，导致 CNY 18 上限被突破。第 195-196 行的 interrupted 恢复分支有同样的 0 默认问题。建议：仅当确实没有任何 model.requested 时才允许 0，否则抛错并保留为待人工核实状态。
- 判断：log不存在/只含头时budget为空即0，恢复分支亦0且把目录记recorded；无法证明没有付费调用。采纳缺失/损坏成本证据fail-closed待核实并预先保守保留。正常日志先有budget再请求，不能仅凭理论不一致声称18元历史已超支。
- 证据：scripts/live-evaluate.py:140

## OCR-2172 · 采纳

- 位置：scripts/live-evaluate.py:168；OCR原严重度：high
- 计划：R1-07 / P2
- 原意见：触发条件：任何人（包括 run 期间由模型代码写入的文件）在事后改动 `.build-cache/M4-live/<dir>` 下的源码或 `target/classes`，再执行 `--audit-only`。该路径只按 `exitCode == 0` 重跑 oracle，从不校验记录的 `sourceSha256` / `cliJarSha256` / `taskSha256`，并直接用新结果覆盖旧的 `independentOracle`，因此一次失败或不同的改动可以被改写为 PASS，审计证据不可复现。建议先用记录的哈希核对当前文件与 jar，不一致时拒绝重跑（或标记为 STALE 并保留原结果）。
- 判断：audit-only确直接用当前目录重跑并覆盖历史independentOracle，未核run.sourceSha256/cliJarSha256。应先比对快照，变化标STALE或保存新独立审计结果，保留原回执而非覆写。
- 证据：scripts/live-evaluate.py:168

## OCR-2173 · 不采纳

- 位置：scripts/live-evaluate.py:117；OCR原严重度：high
- 计划：无
- 原意见：触发条件：CLI 的 stdout 同时承载模型流式输出（CliApplication 的 StreamingLlmClient 直接 print 到同一 out），模型文本若出现以 `APPROVAL ` 开头的行即被当成审批提示。其一，缺少 ` tool=`/` arguments=` 时 `split(...)[1]` 抛 IndexError，脚本在付费运行中途崩溃且不写 ledger；其二，伪造一个能在 allowed() 通过的 `APPROVAL x tool=write_file arguments={...}` 行会让脚本向 stdin 写 `y `，而 CLI 的 TerminalInput 会把这行当作**当前真实挂起提示**的答复，等于绕过了 allowed() 对真实调用的判定。建议：只在 ` tool=`、` arguments=`、` target=` 全部存在时解析，并从 approval.requested 事件/提示 id 校验来源；不符合格式的行记录后忽略，而不是写 stdin。
- 判断：利用前提是模型流式stdout与审批混流，但本脚本生成config未启用streaming；CliApplication只有streaming==true才原文打印模型流。当前APPROVAL由可信interact生成，未证明模型可伪造该行。未来若开启流式，应另做结构化提示关联，不能作为当前绕过结论。
- 证据：scripts/live-evaluate.py:117

## OCR-2174 · 采纳

- 位置：scripts/live-evaluate.py:39；OCR原严重度：medium
- 计划：R1-07 / P1
- 原意见：触发条件：`target/classes` 中出现默认包 `EvaluationOracle.class`（例如审批过滤被放宽/绕过、fixture 目录自带编译产物、或 `target/` 被复制进来）。类路径把模型编译输出排在受信 oracle 目录之前，同名类会遮蔽 `EvaluationOracle`，它只需打印 `INDEPENDENT ORACLE PASS` 即可让 `stdout` 证据造假，所谓“独立”校验失效。建议把 oracle_dir 放在类路径最前，并给 oracle 使用独立包名/G-P 保护（如 `io.jharness.oracle.EvaluationOracle`），同时对加载到的类的 code source 做校验。
- 判断：classpath确把模型target/classes放在oracle_dir前，且默认包名EvaluationOracle可被同名模型编译类遮蔽。可信oracle先行、专用包/独立加载与CodeSource校验；不以只打印PASS作为可信性。
- 证据：scripts/live-evaluate.py:39

## OCR-2175 · 部分采纳

- 位置：scripts/live-evaluate.py:52；OCR原严重度：medium
- 计划：R1-07 / P1
- 原意见：触发条件：`edit_file` 同时传入 `content`（schema 未声明禁止附加属性，WorkspaceToolsPlugin 只列 required，不设 additionalProperties:false），此时 `args.get("content", ...)` 取到的是 content，真正落盘的 `newText` 完全不被黑名单检查；即使没有该键，Java 的 `\u0053ystem.getenv`、`\u006aava.net` 也会在 javac 词法前还原，子串黑名单同样失效。而这段代码是唯一的边界：`mvn test` 里模型写的 Java 以进程权限运行，`java.nio.file` 等未被过滤，可读写 fixture 之外的路径（包括本脚本随后信任的 `docs/testing/M4-live-results.json`、ledger 与 jar）。建议检查该次调用的所有文本字段（path/content/oldText/newText/patchText 拼接后判定），并把“越界写入”视为致命错误，而不是依赖子串黑名单。
- 判断：allowed对edit_file优先取content而实际写newText，且Java Unicode词法/文件API足以绕过字符串黑名单，不能把它视隔离。采纳审核真实字段并将不可信编译执行移入最小权限环境；普通模型代码能写仓库外路径不代表生产工具权限已被绕过，是此付费辅助脚本执行边界不足。
- 证据：scripts/live-evaluate.py:52

## OCR-2176 · 采纳

- 位置：scripts/live-evaluate.py:94；OCR原严重度：medium
- 计划：R1-07 / P2
- 原意见：触发条件：calculator fixture。goal 明确要求“先读取 pom.xml、Calculator.java 和已有测试，再修改已有 CalculatorTest.java”，但 `--require-read` 只覆盖 `pom.xml` 与 `Calculator.java`，`test` 不在必读列表中，因此 harness 无法强制“先读后改”的必需顺序，未读测试直接改写也能通过校验。建议 calculator 分支追加 `--require-read test`（broken-project 不应包含它），使校验与该 fixture 的 goal 一致。
- 判断：calculator goal明确先读已有测试，但argv require-read仅pom/source；expect-file不会证明读取顺序。calculator分支加test必读并保留broken-project不改测试要求。
- 证据：scripts/live-evaluate.py:94

## OCR-2177 · 部分采纳

- 位置：scripts/live-evaluate.py:107；OCR原严重度：low
- 计划：R1-07 / P1
- 原意见：触发条件：一次合法运行耗时超过 660 秒。配置为 `durationSeconds: 600` + `requestTimeoutSeconds: 90`，最大合法耗时约 690 秒加启动开销，因此 660 秒的 deadline 会把仍在预算内的付费运行判为超时并请求取消（白付一次费用）。另外该分支在 `finally` 之后直接 raise，`cli-output.log` 不写、ledger 不更新，本次已产生的成本只能依赖下次启动的恢复扫描（若日志无 budget 事件则丢失，见上面的 0 默认问题）。建议按 `durationSeconds + requestTimeoutSeconds + 余量` 计算 deadline，并在 finally/异常路径里同样落盘已收集输出与成本。
- 判断：660秒异常路径在finally之后直接抛出，cli-output和成本收据不会落盘是真实取证缺口。采纳异常/取消也写有界脱敏输出并保守保留成本；duration600是总预算，不能机械相加90声称690都是合法运行或简单延长超时。
- 证据：scripts/live-evaluate.py:107

## OCR-2178 · 部分采纳

- 位置：sdk/examples/run.py:8；OCR原严重度：medium
- 计划：R6-03 / P3
- 原意见：`JAVA_HOME`、`JHARNESS_SDK_JAR`、`JHARNESS_SDK_WORKSPACE` 全部用 `os.environ[...]` 直接取值：任一变量缺失时只会抛出裸 `KeyError`（消息里只有变量名，没有上下文），`JAVA_HOME` 指向的目录缺少 `bin/java` 时也只会在子进程启动处得到 `PROCESS_START_FAILED`，示例使用者很难定位问题。建议集中校验并给出明确的诊断信息（同时避免把环境变量值原样打印，防止泄露本机路径/凭据）。
- 判断：示例直接取前置环境，缺失裸KeyError/路径TypeError；README要求前置但补缺项/文件存在诊断更清楚，无需改SDK协议。
- 证据：sdk/examples/run.py:8

## OCR-2179 · 部分采纳

- 位置：sdk/examples/run.py:15；OCR原严重度：high
- 计划：R6-03 / P2
- 原意见：轮询的终态集合不完整：任务还有一个真实的终态 `VERIFIED`（见 HarnessWebServer 中 `task.phase = "VERIFIED"`，以及测试里 `Set.of("FINISHED", "UNKNOWN", "VERIFIED")` 的终态判断）。若本次任务被外部验证为 VERIFIED，此循环永远不会命中终态，只能白白轮询到 35s 截止时间；而超时后循环直接落空，后面照样打印半成品状态并以退出码 0 结束，调用方无法区分“正常结束”和“超时未完成”。另外循环在每次迭代都对同一 session 发起 `result` RPC，且超时判断还放在 RPC 之后，会多打一次请求。建议把终态提取为集合、只取一次状态快照，并在超时时显式失败。
- 判断：示例仅FINISHED/UNKNOWN，35秒后不检查终态便打印报告；finally/with会close所以非永久残留。采纳VERIFIED及明确未完成/取消提示。
- 证据：sdk/examples/run.py:15

## OCR-2180 · 部分采纳

- 位置：sdk/examples/run.py:17；OCR原严重度：medium
- 计划：R6-03 / P3
- 原意见：这里有两处问题：1) 再次单独调用 `client.result(session)`，它与循环条件里那次调用是不同的快照，打印出的 phase 可能与终止循环的 phase 不一致（多一次 RPC，也可能多出一次失败机会）；2) `UNKNOWN` 被视为正常终态后，脚本不加区分地继续 `search`/`report` 并正常退出，而 UNKNOWN 表示结果不确定、不得当作成功或重放，示例脚本应把它当作失败分支处理。建议直接打印循环已经捕获的状态，并对 UNKNOWN 单独返回非零退出码。
- 判断：可复用循环终态快照减少多次取样；UNKNOWN仍允许只读search/report，不是重放。仅采纳UNKNOWN恢复提示，不阻止事实读取。
- 证据：sdk/examples/run.py:17

## OCR-2181 · 采纳

- 位置：sdk/examples/run.py:11；OCR原严重度：low
- 计划：R6-03 / P3
- 原意见：注释给出的恢复路径与实际 API 不一致：`JHarnessClient` 没有 `request.get` 方法（SDK 只暴露 `open_session/send_input/result/report/search/...` 等包装，底层方法名 `request.get` 需通过 `client.call('request.get', {'requestId': ...})` 调用）。示例代码这么写会把使用者引向一个不存在的方法名，建议改为可直接复制的真实调用，或明确说明这是底层 RPC 方法。
- 判断：注释request.get没有对应高层方法，应明确client.call('request.get',参数)和保留requestId；底层call已有能力。
- 证据：sdk/examples/run.py:11

## OCR-2182 · 不采纳

- 位置：scripts/smoke-candidate.py:59；OCR原严重度：high
- 计划：无
- 原意见：被测 CLI 及其加载的候选代码继承了父进程的**全部**环境变量（`os.environ` 展开），因此宿主的真实凭据（如 ANTHROPIC_API_KEY / OPENAI_API_KEY / 云厂商 token、CI secret）对候选产物完全可见，可被写盘或外发；而第 92 行与第 163 行的脱敏断言只检查 fixture 值 `m9-local-fixture-only`，真实宿主凭据泄漏不会被发现，烟雾测试因而不是 hermetic 的，也无法起到隔离作用。建议改为最小白名单（Windows 下仍需 SystemRoot/WINDIR/PATHEXT/TEMP 才能启动），只注入 fixture 密钥。
- 判断：浏览器/平台/候选冒烟启动受信任构件继承环境不是工具白名单边界，夹具覆写专用合成key；无已证实真实凭据被使用。若需隔离可显式env，但不能认现有继承即安全漏洞。
- 证据：scripts/smoke-candidate.py:59

## OCR-2183 · 采纳

- 位置：scripts/smoke-candidate.py:92；OCR原严重度：high
- 计划：R0-01 / P1
- 原意见：整个脚本的核心校验（manifest 摘要、进程退出码、脱敏、会话文件格式、交付文件内容、cancel/UNKNOWN 语义等）全部用 `assert` 实现，而这些都属于对外部产物/外部进程结果的运行时校验：一旦用 `python -O`（或 `PYTHONOPTIMIZE=1`，CI 中常见）运行，所有 assert 被剥离，脚本会一路执行到第 164 行写出 `status: "PASS"`，在完全没有校验产物的情况下产出绿色报告——这比不跑更危险。触发条件：`python -O scripts/smoke-candidate.py`。建议引入显式的 `require()` 辅助函数（`if not cond: raise RuntimeError(...)`）替换所有 assert，并且仅在全部检查通过后才写 PASS。
- 判断：smoke-candidate无not __debug__防护，外部产物/进程结果断言全用assert，-O剥除后仍写PASS。入口拒绝优化模式或用显式require；优化模式负例不得产出PASS。
- 证据：scripts/smoke-candidate.py:92

## OCR-2184 · 采纳

- 位置：scripts/smoke-candidate.py:70；OCR原严重度：high
- 计划：R6-01 / P1
- 原意见：manifest.json 来自候选 ZIP 内部（候选可控），这里把 `name` 直接 join 到 `install` 后读取文件：若条目为绝对路径（`/etc/passwd`）或含 `..`（`../../../../etc/shadow`），`Path` 的 join 会逃出安装目录，读取安装树之外的主机文件，构成文件系统边界缺陷（上面的 ZIP 条目校验没有复用到 manifest 条目上）。另外校验只覆盖 manifest 列出的条目，ZIP 中未列出的文件（包括随后实际被执行的 `jharness` / `jharness.cmd`）完全不会被校验，"manifest 校验通过" 并不能说明被执行的内容未被篡改。触发条件：构造含越界条目或部分条目 manifest 的候选 ZIP。建议对每个条目做与 ZIP 条目相同的路径规范化校验，并断言 manifest 覆盖安装目录内的全部文件（至少覆盖将被执行的启动器）。
- 判断：ZIP名称做边界检查但manifest.files名称未经同等验证直接install/name读文件，也未要求manifest覆盖全部安装文件；可逃逸读并让启动器未被摘要约束。规范化所有manifest路径、拒绝越界/重复并验证完整集合。
- 证据：scripts/smoke-candidate.py:70

## OCR-2185 · 部分采纳

- 位置：scripts/smoke-candidate.py:34；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：fixture 处理器对每个请求都无条件 `replies.get(timeout=10)`：只要 CLI 多发一次请求（重试、健康探测、版本变化新增的请求、或 `doctor` 未来改成探活），handler 线程就会抛 `queue.Empty`，既不返回响应也不记录原因，CLI 侧表现为连接被重置/请求超时（最终落到 40s 的 subprocess 超时或退出码不匹配），失败信息与真实原因完全脱节，且异常只在服务线程里丢弃（`log_message` 被覆盖但仍会向 stderr 打 traceback）。另外 `self.headers["Content-Length"]` 在请求使用 chunked 编码时会抛 KeyError，同样是静默失败。建议对空队列显式返回 500/记录，并对缺失的 Content-Length 做兜底。
- 判断：固定本地CLI按Content-Length发请求，空replies表示意外请求，应让测试明确失败。采纳服务线程记录失败原因并受控500/长度错误响应；不为这个封闭测试服务实现任意chunked协议扩展。
- 证据：scripts/smoke-candidate.py:34

## OCR-2186 · 部分采纳

- 位置：scripts/smoke-candidate.py:133；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见："RUNNING" 控制文件的探测过于脆弱：`p.read_text()` 与 glob 之间存在竞态，CLI 正在写入/删除控制文件时会抛 `FileNotFoundError` 或读到半截内容导致 `UnicodeDecodeError`，直接让整个烟雾测试失败；匹配串 `"\nstatus=RUNNING\n"` 还要求 status 既不在首行、且后面带换行符，控制文件格式一旦变化（首行 status、末尾无换行）就会静默匹配失败并触发 `assert len(controls) == 1`。触发条件：CLI 侧控制文件格式/写入顺序变化或写入时机紧邻探测。建议用 `(?m)^status=RUNNING$` 的行级匹配并加短重试，遇到文件消失时跳过。
- 判断：等待provider已收到请求后才读controls，控制文件短暂变化仍可能使read_text抛错；采纳有界重试/一致快照。status格式是自有生产协议，不应无依据改为宽松正则接受格式漂移。
- 证据：scripts/smoke-candidate.py:133

## OCR-2187 · 部分采纳

- 位置：scripts/smoke-candidate.py:0；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：脱敏检查的覆盖面不足：只扫描每次运行的 stdout/stderr 与 `workspace/.jharness` 下的文件，而安装目录（`install`，含子目录日志、配置文件）、进程 cwd、脚本自身写出的 `target/candidate-smoke.json` 等位置都不在扫描范围内。若候选 CLI 把密钥写入安装目录内的日志或其它落盘位置，这里仍会判定 PASS，脱敏检查给出的是假阴性。建议对 `install`、`workspace` 递归扫描（排除候选 ZIP 本身），并对最终 `report` 文本也做一次检查。另外第 91 行的失败信息会把原始 stdout/stderr 整体拼进异常消息，若真的是脱敏失效场景，会把密钥值打进 CI 日志，建议只输出摘要（命中的行号/长度）。
- 判断：异常assert会把stdout/stderr原文包含在异常元组，若合成key回显会写日志；扫描范围也只含metadata。采纳失败诊断先脱敏及明确安装/工作区扫描范围，不承诺扫描整个宿主任意cwd发现所有秘密。
- 证据：scripts/smoke-candidate.py:1

## OCR-2188 · 不采纳

- 位置：scripts/smoke-candidate.py:0；OCR原严重度：low
- 计划：无
- 原意见：`sys` 与 `time` 均未在该脚本中使用（无 `sys.*`/`time.*` 调用），属于未使用的导入，建议删除；若按上面建议给控制文件探测加 `time.monotonic()/sleep` 重试，则只保留 `time`。
- 判断：未使用sys/time属低影响清理，与本轮正确性/安全修复无实质关系，可随真正修改顺手整理，不单列阶段。
- 证据：scripts/smoke-candidate.py:1

## OCR-2189 · 部分采纳

- 位置：scripts/smoke-candidate.py:171；OCR原严重度：low
- 计划：R0-02 / P2
- 原意见：`output.write_text(...)` 在 `finally` 中执行但未做任何保护：若进入 try 前的路径问题（例如 `target/` 不存在导致第 60 行的 `TemporaryDirectory(dir=ROOT / "target")` 抛 `FileNotFoundError`）或磁盘写入失败，finally 里这次写盘会再抛一个 `FileNotFoundError`/`PermissionError`，把真正的失败原因（原始 traceback）掩盖掉。另外 `archive.read_bytes()`（第 26 行）在 try 之外，归档缺失时根本不会写出 INCOMPLETE 报告。建议先 `mkdir(parents=True, exist_ok=True)` 并在此处捕获异常、或在 finally 中仅做 best-effort 写盘。
- 判断：archive.read_bytes在try前，缺包不写INCOMPLETE；output写失败亦无单独诊断。采纳统一入口失败收据及原子写/保留原始错误，不使用吞掉写入错误后返回成功的best-effort。
- 证据：scripts/smoke-candidate.py:171

## OCR-2190 · 部分采纳

- 位置：scripts/test-windows-credential-compat.py:54；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：使用 jar 前没有任何前置校验：jar 不存在时 javac 只会报 `cannot find symbol SystemCredentials` 之类的间接错误；更危险的是 jar 可能是旧构建产物（例如最近一次打包失败后残留），此时探针验证的是过期实现，却仍会写出 `passed=true` 且 `currentJarSha256` 与这个陈旧 jar 一致，下游按同一 hash 比对无法发现，形成假阳性。另外 `os.environ['JAVA_HOME']` 缺失时直接抛 `KeyError` traceback。建议在编译前断言 `JAVA_HOME` 可解析、`javac.exe`/`java.exe` 与 jar 均存在，并校验 jar 的 mtime 不早于相关 Java 源码（否则要求先重新构建）。
- 判断：缺JAVA_HOME/JAR会封闭失败，不会验证成功；但应显式验证工具链路径并以当前构建输入摘要绑定artifact，mtime只是辅助不可作为唯一真实性。采纳与2191统一新运行身份，拒绝仅凭旧JAR一致hash就称过期已发生。
- 证据：scripts/test-windows-credential-compat.py:54

## OCR-2191 · 采纳

- 位置：scripts/test-windows-credential-compat.py:66；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：报告文件固定为 `.build-cache/M10.5.4-windows-compat.json`，但只在本脚本成功走到末行时才覆盖写入。若本次运行在 `json.loads`/javac/JVM 超时处中止（或写入过程被中断），上一次通过的报告会原样保留；下游 `scripts/collect-m1054-native-ci-evidence.py` 只要 `currentJarSha256` 与当前 jar 一致（同一 jar 未重建时成立）就会把旧报告当成新一次通过，形成“陈旧证据”。建议在脚本开始时先删除旧报告（或写入带明显失败状态的占位报告），并用临时文件 + `os.replace` 原子写入。
- 判断：成功末尾才覆盖固定windows-compat.json，若新run在javac/JVM前失败旧PASS仍在且同JAR collector可能复用。开始记录本run INCOMPLETE，成功原子发布并由collector核runId/退出状态。
- 证据：scripts/test-windows-credential-compat.py:66

## OCR-2192 · 部分采纳

- 位置：scripts/test-windows-credential-compat.py:62；OCR原严重度：high
- 计划：R0-01 / P2
- 原意见：在检查 `result.returncode` 之前就无条件解析 `result.stdout`：JVM 崩溃、被 `DestroyForcibly`、stdout 为空或混入非 JSON 输出时，`json.loads` 会抛 `JSONDecodeError`，脚本直接以 traceback 退出，既不会写出失败报告，也完全丢弃了 `result.stderr` 中的诊断信息（JVM 侧只抛无 message 的 `IllegalStateException`，stderr 是唯一线索）。建议先判断退出码/输出是否为空，失败时构造 `passed=False` 的报告并把 stderr 尾部写入报告。
- 判断：parse stdout在returncode判定之前，JVM崩溃丢stderr且不更新旧报告；采纳先检查code/输出结构并写失败收据，stderr必须有界脱敏，不能原文尾部不加处理就写。
- 证据：scripts/test-windows-credential-compat.py:62

## OCR-2193 · 采纳

- 位置：scripts/test-windows-credential-compat.py:61；OCR原严重度：high
- 计划：R1-04 / P2
- 原意见：凭据清理只存在于 Java 探针的 `finally` 块中。一旦 `subprocess.run(..., timeout=120)` 触发 `TimeoutExpired`（JVM 被强杀，finally 不会执行），或 JVM 直接 abort，探针写入的真实 Windows 凭据管理器条目会永久残留；同时 `TimeoutExpired` 未被捕获，脚本以 traceback 退出且不产生报告。建议把 `target` 从 Python 侧生成并以参数传给探针，外层用 try/except 捕获超时，超时后以“仅删除”模式再次启动探针（或记录残留 target 便于清理），保证异常路径下也会尝试删除。
- 判断：target只在Java里随机生成，JVM被timeout强杀finally无法删除，Python既不知道target也无外层清理。Python生成专用target并传入，超时后只清理本次目标并报告结果；不触碰其它凭据。
- 证据：scripts/test-windows-credential-compat.py:61

## OCR-2194 · 部分采纳

- 位置：scripts/test-windows-credential-compat.py:17；OCR原严重度：medium
- 计划：R0-01 / P3
- 原意见：`git show fdd326b:...` 没有超时也没有异常处理：在浅克隆、squash/rebase 后历史被重写的仓库中该对象不存在，`check_output` 抛 `CalledProcessError`，脚本以原始 traceback 失败且不产出可诊断报告；在 git 挂起（如凭证提示、大对象）时还会无限阻塞，使整个兼容性验证卡死。建议加 `timeout` 并捕获 `CalledProcessError`/`TimeoutExpired`，以明确提示（提交对象缺失或 git 超时）失败退出。
- 判断：git show读取固定本地commit不会触发远端凭据，但浅克隆对象缺失会直接退出而不写本次失败收据。采纳明确历史对象存在检查和有界git退出诊断，不将本地show说成可能自动联网认证。
- 证据：scripts/test-windows-credential-compat.py:17

## OCR-2195 · 不采纳

- 位置：scripts/test-windows-credential-compat.py:18；OCR原严重度：medium
- 计划：无
- 原意见：用无边界全局 `str.replace` 重命名历史源码：任何包含 `SystemCredentials` 的子串（其他标识符前缀、注释、以及最关键的字符串字面量，例如凭据 target 前缀或配置键）都会被一并改写，可能编译失败或静默改变被测旧实现的语义；若历史版本中类声明名不同，replace 会“无声无息”地不做任何改动，随后的 javac 才以晦涩的符号错误暴露问题。建议用词边界正则替换，并在写入前断言重命名确实命中了类声明（同时人工确认字符串字面量未被改写）。
- 判断：被替换历史源码固定fdd326b，当前其中SystemCredentials是目标类/构造器名称；没有证明其它字符串/标识符语义被改坏。为未来任意历史版本设计Java重命名器没有必要，应继续固定精确受审输入。
- 证据：scripts/test-windows-credential-compat.py:18

## OCR-2196 · 采纳

- 位置：sdk/examples/loopback_provider.py:7；OCR原严重度：high
- 计划：R3-04 / P3
- 原意见：Request parsing in the loopback provider is unguarded: a non-numeric/garbled `Content-Length` raises ValueError, and a non-dict body, missing `messages` or a null/non-dict message element raises TypeError/KeyError/AttributeError — in every case the handler dumps a traceback and drops the connection instead of returning a controlled 400. Catch parse errors, type-check the body, and distinguish missing/invalid/oversized length.
- 判断：独立公开loopback示例直接int/JSON/body.messages访问，非法输入会断连接并traceback；补Content-Length/对象/消息结构错误400/413，保留仅loopback不转发。
- 证据：sdk/examples/loopback_provider.py:7

## OCR-2197 · 部分采纳

- 位置：sdk/examples/loopback_provider.py:11；OCR原严重度：medium
- 计划：R3-04 / P3
- 原意见：`self.rfile.read(size)` 只调用一次且 handler 未设置 socket 超时：TCP 的 `read(n)` 可能返回少于 n 字节（客户端声明了更大的 `Content-Length` 后不发完、或慢速发送），此时 `json.loads` 会失败；而 `ThreadingHTTPServer` 为每个连接起一个线程且无超时，半开/慢速连接会长期占用线程，累积后可耗尽进程线程数。建议在 `Provider` 上设置 `timeout`（如 10 秒），并循环读取直到读满 `size` 或对端关闭，读不满时返回 400。
- 判断：handler确无socket读取超时，半发Content-Length可占线程；采纳每连接超时/长度上限与断流错误处理。BufferedReader.read(n)会等满或EOF，不是一般一次TCP分片必读少；HTTPServer.timeout仅影响serve等待而非socket读取，不能照原建议直接设置类timeout算修复。
- 证据：sdk/examples/loopback_provider.py:11

## OCR-2198 · 部分采纳

- 位置：scripts/verify-m8-closure.py:70；OCR原严重度：medium
- 计划：R0-02 / P2
- 原意见：源码快照覆盖不完整，使输出中的 `sameJavaPomSources: true` 名不副实：这里只纳入各模块 pom.xml 与 `src/**/*.java`，模块缺少 `src` 目录时 `rglob` 静默返回空集合而不报错，`src/main/resources`、配置文件等也未纳入哈希；同时整个流程没有任何环节把最终的 `jar_sha` 与这份源码快照关联起来（JAR 只通过探针报告里的 jarSha256 自证），因此“同一份源码 + 最终 JAR”这一结论无法由本脚本证明。建议校验每个模块的 src 存在、把 src 下全部文件纳入快照，并明确断言 JAR 与快照的绑定关系（或把结论口径收窄）。
- 判断：字段名sameJavaPomSources本就限定Java/POM，不应强说必须含所有resources才名副其实。采纳共享现行门禁的完整输入/JAR绑定，不追溯重标M8；probe确核最终JAR hash与时间，不是完全无关联。
- 证据：scripts/verify-m8-closure.py:70

## OCR-2199 · 部分采纳

- 位置：scripts/verify-m8-closure.py:114；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：结果落盘与校验共用同一个 try：`args.output.write_text` 失败（父目录不存在、只读、磁盘满）时会被当成校验失败，打印“M8.4 closure REJECTED”并返回 1，而实际结论是校验通过、仅写文件失败，容易误判门禁结果。另外 write_text 非原子，异常中断会留下半截 JSON 被后续流程误用。建议把写文件移出校验 try 并给出区分的失败信息（或先写临时文件再 rename）。
- 判断：写失败导致非零拒绝符合门禁fail-closed，但可区别VALIDATION_FAILED/PUBLISH_FAILED并原子发布避免半JSON。合并现行证据事务任务，旧M8收据不改。
- 证据：scripts/verify-m8-closure.py:114

## OCR-2200 · 部分采纳

- 位置：scripts/verify-m8-closure.py:51；OCR原严重度：low
- 计划：R0-02 / P3
- 原意见：同一个日志文件被读三次（第 50 行 `sha(log)` 读字节，这里又两次 `read_text`）：完整 Maven 日志通常较大，重复 IO 浪费；更重要的是哈希校验与内容校验基于不同次读取，理论上文件在两次读取之间被替换时会出现“哈希匹配但内容含 BUILD FAILURE”的错配。建议读取一次字节后同时用于哈希与文本判定。
- 判断：sha与两次read_text确为独立读取；采纳一次bytes用于摘要和SUCCESS/FAILURE检查，减少读取并保证内容一致，不宣称现有静态本地证据已被攻击替换。
- 证据：scripts/verify-m8-closure.py:51

## OCR-2201 · 部分采纳

- 位置：scripts/verify-m8-closure.py:117；OCR原严重度：high
- 计划：R0-02 / P3
- 原意见：异常捕获范围不足：verify() 依赖大量外部 JSON/pom 字段，`ET.parse` 抛出的 `ET.ParseError`（SyntaxError 子类）与 `report.get(...)` 返回 None 后再调用 `.startswith()/.items()` 产生的 `AttributeError` 都不在捕获元组里。当 pom.xml 损坏或证据 JSON 缺字段（如 javaVersion=null、sourceSha256=null）时，脚本不会走“M8.4 closure REJECTED”分支，而是打印原始 traceback 并以非约定退出码结束，破坏该脚本作为门禁的判定语义。建议在元组中补上 `AttributeError` 与 `ET.ParseError`。
- 判断：ET.ParseError/AttributeError确不在except中，仍非零失败但诊断风格不一致。采纳当前共享验证器明确输入结构错误处理，不为了旧文件字段None把异常当通过。
- 证据：scripts/verify-m8-closure.py:117

## OCR-2202 · 部分采纳

- 位置：scripts/verify-m8-closure.py:88；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：探针时间校验存在时区与 TOCTOU 缺陷：`datetime.fromisoformat` 在 capturedAt 不带偏移量时返回 naive datetime，`.timestamp()` 会按本机时区解释——在东半球时区会把有效探针判为“predates final JAR”（误拒），在西半球时区则会让实际早于 JAR 的过期探针通过（误放行）。此外 `jar.stat().st_mtime` 在每次循环中重新读取，与第 79 行一次性计算的 `jar_sha` 不属同一时刻快照。建议要求 capturedAt 必须带 tzinfo，并在计算 jar_sha 时一并缓存 mtime。
- 判断：fromisoformat接受naive且timestamp用本地时区，缺offset输入可错误判新鲜；采纳要求offset与冻结JAR元数据快照。现有capturedAt带Z/offset正常，不否认历史执行结果。
- 证据：scripts/verify-m8-closure.py:88

## OCR-2203 · 不采纳

- 位置：scripts/verify-m8-closure.py:109；OCR原严重度：low
- 计划：无
- 原意见：文件系统边界不一致：从 JSON 派生的证据路径都经过 `local()` 约束在 workspace 内，但 `--evidence-dir`、`--jar`、`--output` 三个命令行参数未经任何解析与边界校验就直接使用。这允许用 workspace 之外（甚至被替换的）证据目录/JAR 生成一份自称 M8.4 closure 的结果，也可以把结论写到任意路径，弱化了“证据只来自本仓库”的保证。建议对三者统一 resolve 后用 `is_relative_to(ROOT)` 校验。
- 判断：--evidence-dir/--jar/--output是本机操作者显式CLI授权路径，可用于校验下载/其它位置JAR，JSON内部路径另local限制。命令行路径不等于不可信报告路径，强制全在ROOT会破坏正常离线审计用法；未声明只读仓库内参数。
- 证据：scripts/verify-m8-closure.py:109

## OCR-2204 · 采纳

- 位置：scripts/test-open-workbench.py:85；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：全部安全/生命周期结论都依赖 `assert`，而 `assert` 在 `python -O`（或 `PYTHONOPTIMIZE`）下会被剥离：此时脚本会跳过鉴权、单实例、退出清理等校验，却仍在第108-111行写出 `"status": "PASS"` 的结果文件，产生虚假认证。这对一个用于归档证据的脚本是实质风险，建议改为显式 `if not cond: raise AssertionError(...)`（或统一的 `check()` 辅助函数），并可在 `main()` 入口拒绝优化模式。
- 判断：test-open-workbench无__debug__门禁，-O会剥离认证/生命周期assert但仍写PASS；与smoke统一拒绝优化模式或显式require，验证-O不产通过收据。
- 证据：scripts/test-open-workbench.py:85

## OCR-2205 · 采纳

- 位置：scripts/test-open-workbench.py:52；OCR原严重度：high
- 计划：R6-03 / P2
- 原意见：启动失败路径没有终止子进程：`p.wait(timeout=15)` 超时会抛出 `subprocess.TimeoutExpired`，既掩盖了本意的 'Launcher readiness timeout' 诊断，又把挂起的 PowerShell 及其 Java 子进程留在后台继续占用端口/工作区锁，污染后续“重复启动必须失败”和“重新启动”等用例。建议捕获 `TimeoutExpired` 后先 kill 并在 Windows 上用 `taskkill /T` 或 `p.kill()` + 等待，再抛出原始错误。
- 判断：readiness超时分支关stdin后wait15若TimeoutExpired，未清理自有PowerShell/Java，可能残留占用锁/端口。补有界自有进程树清理并保留原超时诊断，不能按旧PID误杀无关实例。
- 证据：scripts/test-open-workbench.py:52

## OCR-2206 · 采纳

- 位置：scripts/test-open-workbench.py:64；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：第66-70行的“关闭后无监听端口”校验不可靠：`urllib.error.HTTPError` 是 `URLError` 的子类，因此宿主仍然存活并返回 401/404/500 时会被最后一个 `except URLError` 静默吞掉，进而被判定为“已停止”。另外 `request(url)` 携带的是原 token，若宿主仍监听但已换用新 token（第92-98行会生成新 token），同样得到 401 并被当作停止成功。这会让第83行断言写入的 `quit gracefully closes owned Java service and leaves no listening port` 成为虚假认证。建议先对失败响应单独处理，并额外用 socket 探测端口是否已不可连接。
- 判断：stop捕获URLError同时包括HTTPError，服务仍返回401/500可被当停止；应单独拒HTTP响应并探测socket不能连接，保持原退出码断言。
- 证据：scripts/test-open-workbench.py:65

## OCR-2207 · 部分采纳

- 位置：scripts/test-open-workbench.py:58；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：`stop()` 在收尾路径上缺少失败保护：(1) 若宿主在 `:quit` 之前已经崩溃，`p.stdin.write/flush` 会抛 `BrokenPipeError`（或已关闭时抛 `ValueError`），把 `finally` 中真正需要上报的原始断言失败掩盖掉；(2) `p.wait(timeout=15)` 超时抛 `TimeoutExpired` 时既不 terminate 也不 kill，Java 子进程会残留并继续监听端口，使下一次运行或“无监听端口”结论失真。建议对写入做异常保护，并在超时分支强制终止后给出明确错误。
- 判断：stop写stdin和wait无异常保护/升级，BrokenPipe或超时可遮原异常并留自有launcher/Java；采纳有界回收自有进程树与原始错误保留，不吞清理失败。
- 证据：scripts/test-open-workbench.py:58

## OCR-2208 · 部分采纳

- 位置：scripts/test-open-workbench.py:42；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：stdout 读取线程创建后没有保存、也没有 join：`stop()` 里对 `output` 的 token/剪贴板泄漏断言可能在读线程尚未读完管道最后几行时执行，从而漏检迟到的敏感输出（例如退出瞬间打印的凭据或错误上下文）；同样 `start()` 中 `p.poll() is not None` 的早退分支也可能在输出未读完时抛出，错误信息不完整。建议把线程对象返回并在断言前 `thread.join(timeout=...)`。
- 判断：读取线程未保留/join，wait进程退出后输出可能尚未读完，迟到秘密检查可漏；采纳有界join/EOF后断完整输出，仍不得打印秘密原文。
- 证据：scripts/test-open-workbench.py:42

## OCR-2209 · 部分采纳

- 位置：scripts/test-open-workbench.py:75；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：临时目录固定放在 `ROOT / '.build-cache'`，但脚本从不创建该目录；在干净检出或清理过后单独运行时会直接抛 `FileNotFoundError`，报错与真实意图（启动器冒烟）无关。建议先 `Path(ROOT / '.build-cache').mkdir(parents=True, exist_ok=True)`，同样也让末尾写入 `M10-launcher-results.json` 更稳健。
- 判断：单独运行test-open-workbench未mkdir .build-cache；正常构建/矩阵会提前建，但干净checkout独立入口可失败。采纳显式创建受管缓存根与前置验证。
- 证据：scripts/test-open-workbench.py:75

## OCR-2210 · 不采纳

- 位置：sdk/python/jharness_sdk.py:48；OCR原严重度：medium
- 计划：无
- 原意见：子进程环境变量没有白名单：默认把完整的 `os.environ`（含云凭据、token、代理口令等继承来的敏感变量）透传给 Java 子进程；而调用方传入 `env` 时又是整体替换而非合并，无法在最小化白名单上叠加 PATH/JAVA_HOME 等必需项。建议默认只透传必需变量（PATH、JAVA_HOME、LANG/LC_ALL、TZ、TMPDIR、Windows 的 SystemRoot 等），并在调用方提供 `env` 时与白名单合并，而不是整体覆盖。
- 判断：SDK按调用者环境或整体env替换启动自有Java是契约，模型key和凭据服务本就依赖该环境；不是不可信命令工具的环境白名单边界。默认剥离会破坏apiKeyEnv，调用方可显式env隔离。
- 证据：sdk/python/jharness_sdk.py:48

## OCR-2211 · 采纳

- 位置：sdk/python/jharness_sdk.py:195；OCR原严重度：low
- 计划：R3-04 / P2
- 原意见：`kill()` 之后的 `self._process.wait(timeout=2)` 未捕获 `subprocess.TimeoutExpired`（进程处于不可中断状态时仍可能超时）。异常会从 `close()` / `__exit__` 抛出，既掩盖 `with` 体内的原始异常，也跳过后面关闭 stdin/stdout/stderr 的循环，造成 fd 泄漏。建议把最后的 `wait` 包在 `try/except subprocess.TimeoutExpired: pass` 中，并把流关闭放到 `finally`。
- 判断：Python close最终kill后wait超时未兜底，会跳_fail/stream.close且遮蔽with原异常；应finally保证清理并有界报告关闭失败。
- 证据：sdk/python/jharness_sdk.py:195

## OCR-2212 · 部分采纳

- 位置：sdk/python/jharness_sdk.py:137；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：The `_expired` FIFO (64 entries) plus the timeout path mishandle ids: an evicted id whose response arrives later hits `future is None` and triggers `_fail` + `_terminate`, killing the whole connection and all pending calls; and when the response arrives between `future.result()` timing out and taking the lock, the id is registered as expired although it was consumed, so entries leak until the fatal path trips. Only register `_expired` when the future is genuinely not done, and treat unknown/evicted ids as a recoverable, non-fatal condition.
- 判断：expired墓碑64项淘汰后迟到响应被判未知杀连接属实；真正未知/重复id fail-closed合理，不能一律忽略。采纳区别已知过期、明确淘汰策略及65次延迟响应回归。
- 证据：sdk/python/jharness_sdk.py:137

## OCR-2213 · 不采纳

- 位置：sdk/python/jharness_sdk.py:56；OCR原严重度：medium
- 计划：无
- 原意见：版本协商不一致：请求里发的是调用方传入的 `protocol_version`，接受判断却硬编码为 `"1.0"`。若调用方传 `"1.1"` 且服务端接受了 `"1.1"`（或回显任意非 1.0 值），客户端会误判 `VERSION_UNSUPPORTED` 并 close 掉一个已经初始化成功的子进程；反之服务端回 `"1.0"` 时也不校验是否与请求值一致。建议：本客户端只支持 1.0 时，在 `__init__` 中直接校验 `protocol_version == "1.0"`（否则本地 `ValueError`），发送常量 `"1.0"`，或改为比较 `self.capabilities.get("protocolVersion") != protocol_version`。
- 判断：客户端仅支持协议1.0，传不支持版本用于拒绝测试；固定上限是1.0客户端自身预算，不能因未来1.1被拒认兼容缺陷，也不能盲信服务端扩大上限。
- 证据：sdk/python/jharness_sdk.py:56

## OCR-2214 · 部分采纳

- 位置：sdk/python/jharness_sdk.py:113；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：写线程失败只调用 `_fail`，不像读线程的 `(ValueError, ...)` 分支那样同时 `_terminate()`；管道破裂/子进程卡死时，子进程既不终止也从未 `wait()` 回收（`Popen` 对象只在 `close()` 里被 wait），若调用方不显式 `close()` 就会留下僵尸或长期挂起进程。同时写线程已退出，`_writes` 中滞留的帧再也不会被消费，后续 `close()` 塞入的 None 哨兵也无人处理。建议统一失败处理：`_fail(...)` 后执行 `_terminate()`（并在后台或 `close()` 中 `wait()` 回收），或在 `_fail` 内部统一终止。读线程 `not frame` 分支同样只 `_fail` 而不回收进程，可一并处理。
- 判断：写线程_fail只标closed不terminate，不显式close可留子进程；调用者仍有close责任。采纳异常路径统一回收，仅终止自有进程。
- 证据：sdk/python/jharness_sdk.py:113

## OCR-2215 · 采纳

- 位置：sdk/tests/client.py:15；OCR原严重度：medium
- 计划：R0-01 / P2
- 原意见：整套校验都建立在 `assert` 上（`check` 与 `rejects` 都是），而 `python -O` / `PYTHONOPTIMIZE=1` 会剥离所有 assert：此时 `check` 仍会 `print("PASS", ...)` 并 append 到 `checks`，`rejects` 也不再比对错误码，脚本最终照样输出 `"passed": true` 且退出码为 0。触发条件：CI 或本地以优化模式运行 `JHARNESS_PYTHON` 时，会得到完全无效的假通过。建议改为显式抛错（`if not condition: raise AssertionError(name)`），`rejects` 中同样用 `raise AssertionError` 代替 assert。
- 判断：check/rejects使用assert且优化模式会剥离，PYTHONOPTIMIZE可经runner环境传入并仍输出passed true。改显式抛错或启动拒__debug__ false，补优化模式拒绝验收。
- 证据：sdk/tests/client.py:15

## OCR-2216 · 部分采纳

- 位置：sdk/tests/client.py:30；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`rejects()` defaults `code=None` and then accepts any `RpcError`, so a timeout/backpressure/disconnect (`REQUEST_TIMEOUT_ACCEPTANCE_UNKNOWN`, `BACKPRESSURE_LIMIT`, ...) counts as a pass for critical negative checks (callId mismatch approval, post-disconnect result, blocked send) — exactly the cases where the approval/write may already have been accepted. Make the expected error code mandatory, match the exact code, and re-assert that no write occurred.
- 判断：rejects code=None允许任意RpcError导致错关联审批/UNKNOWN续跑错误原因不明；采纳具体错误码与无写事实复查。断线后CLIENT_CLOSED是合理预期，不能要求只INVALID_APPROVAL。
- 证据：sdk/tests/client.py:30

## OCR-2217 · 部分采纳

- 位置：sdk/tests/client.py:111；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：`check(name)` is called without a condition (default True) and the triggering call's return value is discarded, so these checks pass regardless of behavior (e.g. the stderr diagnostic leaking into API results, or the owned peer still running after `close()`). Assert on the actual return value / `client._process.poll()`.
- 判断：check无条件有时仅记录前序rejects正确，但stderr调用结果被丢、故障peer close后未查poll确不足证标签。补具体返回对象无secret和每个peer终止断言，不全盘删除记录型check。
- 证据：sdk/tests/client.py:111

## OCR-2218 · 采纳

- 位置：sdk/tests/client.py:61；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：续轮存在竞态：Java 侧 `input.send`（continuation）只是把 turn 入队后立即返回回执（见 HarnessWebServer#enqueue，enqueue 不会同步改变 phase），因此此刻 `session.result` 的 phase 仍是上一轮留下的 `FINISHED`，这里的 `until` 会**立刻返回**，并没有等待第二轮真正结束。随后的事件分页就可能只抓到 1 个 `turn.started`，使 `check('paged real events retain multiple turn boundaries', ...)` 随机失败，或在事件未落盘时判定 artifact/脱敏结果。建议先等到第二轮开始（`turn.started` 计数达到 2 或 phase 离开 `FINISHED`），再等回到 `FINISHED`。
- 判断：send_input续轮只入队，紧接result可仍旧FINISHED，等待旧phase立即过后断turn数量会抖动。按返回submission/turn身份等待新轮开始与终结，TS同类流程一起补。
- 证据：sdk/tests/client.py:61

## OCR-2219 · 部分采纳

- 位置：sdk/examples/run.ts:4；OCR原严重度：high
- 计划：R6-03 / P3
- 原意见：第4行直接用 `!` 断言解引用 `JAVA_HOME` / `JHARNESS_SDK_JAR` / `JHARNESS_SDK_WORKSPACE`，没有校验：变量缺失时 `join(undefined, ...)` 只会抛出难以定位的 `TypeError: Path must be string`；变量为空串时则会以 `java -jar ''` 启动子进程，最终只得到一个笼统的 `PROCESS_START_FAILED`（`start()` 内部的 initialize 也会超时失败），无法判断是哪个变量配错。此外这里没有向 `JHarnessClient.start` 传 `env`，而客户端实现是 `env: options.env ?? process.env`，等于把宿主进程的全部环境变量（可能含 token、密钥等凭据）原样继承给 Java 子进程，违反环境变量白名单要求。建议先显式校验三个变量存在且非空，再按最小集合构造 `env` 传入。
- 判断：示例直接取前置环境，缺失裸KeyError/路径TypeError；README要求前置但补缺项/文件存在诊断更清楚，无需改SDK协议。
- 证据：sdk/examples/run.ts:4

## OCR-2220 · 部分采纳

- 位置：sdk/examples/run.ts:9；OCR原严重度：high
- 计划：R6-03 / P2
- 原意见：轮询/终态处理有两处问题：(1) 循环条件把 `await client.result(id)` 放在 `Date.now()<end` 之前求值，即使 35s deadline 已过仍会多发一次 `session.result` RPC；超时退出循环后也没有任何终态判断，直接继续 result/search/report 并以 0 退出，任务超时（仍 RUNNING/WAITING）会被当作成功。(2) 第10行又重新调用 `client.result(id)`，重复第9行循环里最后一次查询并丢弃其快照，多打一次 RPC 还可能打印与判定不一致的状态；且 UNKNOWN 与 FINISHED 同等对待，而 README 规定 UNKNOWN 表示结果未知、禁止自动续跑，这里却照常打印并 0 退出。建议：循环结束后复用循环中拿到的 state，按最后一次 phase 判定，非 FINISHED/UNKNOWN 抛出错误或置非零退出码，对 phase==='UNKNOWN' 单独提示并以非零退出码结束；并把 35000/100 提取为具名常量（如 `POLL_TIMEOUT_MS`/`POLL_INTERVAL_MS`）。
- 判断：示例仅FINISHED/UNKNOWN，35秒后不检查终态便打印报告；finally/with会close所以非永久残留。采纳VERIFIED及明确未完成/取消提示。
- 证据：sdk/examples/run.ts:9

## OCR-2221 · 采纳

- 位置：sdk/examples/run.ts:6；OCR原严重度：medium
- 计划：R6-03 / P3
- 原意见：第6-8行对 `openSession()` 与 `sendInput()` 的返回字段没有任何校验，直接用 `String()` 转换：当协议返回缺少 `handle`/`sessionId`（或值为空串/null）时，会被静默转成字符串 `"undefined"`/`"null"`（`""` 则被直接透传），随后所有 result/input/搜索请求都带着这个非法标识执行，只能得到难以定位的 INVALID_PARAMS 或长时间轮询到超时。建议先判断 `typeof opened.handle==='string' && opened.handle.length>0`、`typeof receipt.sessionId==='string' && receipt.sessionId.length>0` 再使用，否则立即抛出带上下文的错误。
- 判断：TS示例String(handle/sessionId)会把undefined/null转为合法字符串继续RPC，应验证必需字段并明确协议错误。
- 证据：sdk/examples/run.ts:6

## OCR-2222 · 不采纳

- 位置：sdk/examples/run.ts:11；OCR原严重度：medium
- 计划：无
- 原意见：第10-12行把会话 result、内容检索命中和 report 整体 `console.log`，既未截断也未脱敏：`search('README')` 会返回工作区文件正文片段，`report` 可能包含命令输出、路径与环境信息，在共享日志/CI 中会泄露敏感内容；同时输出长度无上界，大工作区下会刷屏或撑爆日志采集。建议只打印白名单字段（如 phase/status/sessionId）、对命中数量与文本长度做上限截断，并对疑似敏感内容做脱敏。
- 判断：result/search/report消费产品已脱敏有界事实，示例显式输出是用途；search查会话正文而非任意工作区文件，未见实际敏感泄漏。
- 证据：sdk/examples/run.ts:11

## OCR-2223 · 部分采纳

- 位置：sdk/examples/run.ts:13；OCR原严重度：medium
- 计划：R6-03 / P3
- 原意见：`try/finally` 只保证关闭，没有 `catch`：`openSession`/`sendInput`/`result` 抛出的 `RpcError`（例如 `REQUEST_TIMEOUT_ACCEPTANCE_UNKNOWN`、`PROCESS_DISCONNECTED`）会以顶层未处理拒绝的形式打印整段堆栈，既没有“哪一步失败、该如何核对 requestId”的友好提示，也无法保证退出码语义（部分路径会以 0 结束）。建议增加 `catch`，输出带步骤上下文的中文错误信息并设置 `process.exitCode=1`，finally 中保留 `client.close()`。
- 判断：示例RpcError直接抛出但finally关闭且错误码无密钥；UNKNOWN应补保留requestId/会话id对账说明，不自动重发。
- 证据：sdk/examples/run.ts:13

## OCR-2224 · 不采纳

- 位置：frontend/src/content.ts:13；OCR原严重度：medium
- 计划：无
- 原意见：关联键非单射：`(turnId??'')+':'+(callId??'')` 用固定分隔符拼接，值中本身可含 ':'，因此 `{turnId:'a',callId:'b:c'}` 与 `{turnId:'a:b',callId:'c'}` 都得到 `a:b:c`；同时 `null` 与 `''`、缺省值全部塌缩为空串（`{turnId:'x'}` 与 `{turnId:null,callId:...}` 无法区分）。Conversation.tsx:14 用该键做 `results.set/requests.has` 覆盖式索引，一旦碰撞：后到的 `tool.completed` 会覆盖前一次调用的结果，且第 20 行 `!requests.has(...)` 判定会把重复键的完成事件当作孤立结果渲染——即完成结果可能被挂到错误的 turn/call 上（与“UNKNOWN 状态不得被重放/结果不得跨调用混淆”的要求冲突）。建议改为单射编码（长度前缀或 JSON 数组），并显式区分 undefined/null/''；注意 frontend/tests/client.test.ts:231 断言了当前 `':legacy'` 格式，需同步调整。
- 判断：api.ts:event对turnId使用identifier，仅允许字母数字下划线连字符；a:b turnId进不了Conversation。callId冒号不使有效域前缀发生所给碰撞；null/缺省为同一legacy无turn约定。
- 证据：frontend/src/content.ts:13

## OCR-2225 · 部分采纳

- 位置：frontend/src/content.ts:5；OCR原严重度：high
- 计划：R4-03 / P3
- 原意见：路径边界只在字符串层校验，且未拒绝百分号编码/未解码：`..%2f..%2fsecret.txt`、`%2e%2e/%2e%2e/x.ts` 中不含字面 '/'、'..' 段和控制字符，扩展名也匹配，因此会通过校验；Markdown.tsx:21 随后把这个 `ref.path` 交给 `onFile(path,line)` 去后端读取文件，若下游做了 URL/decoded 解析（或对 % 做二次解码）即可越界到工程目录之外。同一行的扩展名白名单 `\.[a-zA-Z0-9]{1,12}$` 还会直接拒绝无扩展名（Makefile、Dockerfile、LICENSE）和扩展名 >12 字符的合法文件，导致这类引用被渲染成“链接不可用”。另外 Windows 盘符路径（`C:\src\a.ts`）在替换反斜杠前后都含 ':'，被整类拒绝。建议：只要出现 '%' 或 '\\' 残留/空段/'.'段就拒绝或先做规范化再校验，把“是否允许访问”的最终判定放到服务端真实路径归一化后（不允许 .. 逃逸根目录），扩展名规则改为显式可选或按白名单配置。
- 判断：无扩展名相对文件确实被拒；盘符绝对路径拒绝是有意工作区限制，百分号经encodedPath再编码且服务端查真实根，不能据此证明越界。仅采纳受控无扩展名引用支持。
- 证据：frontend/src/content.ts:5

## OCR-2226 · 采纳

- 位置：frontend/src/content.ts:6；OCR原严重度：medium
- 计划：R4-03 / P3
- 原意见：行号只做上界截断，没有下界/正整数校验：输入 `src/A.java:0` 会返回 `{path:'src/A.java',line:0}`，而行号语义是 1-based，消费方（Markdown.tsx:21 → onFile → ContentReader）拿到 0 后定位到非法/首行之前的位置。建议在转换为数字后强制 >=1。
- 判断：fileReference只Math.min限制行号上界，:0会生成line=0；应约束1-based正整数。
- 证据：frontend/src/content.ts:6

## OCR-2227 · 不采纳

- 位置：frontend/src/content.ts:4；OCR原严重度：medium
- 计划：无
- 原意见：尾部 `:digits` / `#Ldigits` 被无条件当作行号剥离，文件名本身含该后缀时会被误解析：字面文件名 `notes:2024` 会被解析成 `{path:'notes',line:2024}`（打开错误的文件/必然失败），`a#L7` 会被解析成 `{path:'a',line:7}`；而 `notes:2024.ts` 又因为整串含 ':' 被直接判为 null，同一类输入结果自相矛盾。建议只接受明确的 `#L<digits>` 形式（':' 形式仅在路径校验通过后才回退推断），或在剥离后缀后校验原串是否被完整解释。
- 判断：文件引用有意采用受限相对路径+行号启发式，不承诺冒号文件名或无scheme外链。HTTPS外链仍正确识别，重复纯解析不是现存缺陷。
- 证据：frontend/src/content.ts:4

## OCR-2228 · 不采纳

- 位置：frontend/src/content.ts:9；OCR原严重度：low
- 计划：无
- 原意见：`fileReference(value)` 的结果被丢弃，只用其真值决定分类：一是同一串要解析两次（Markdown.tsx:20 紧接着又调一次 `fileReference(target)`）；二是分类优先级由文件启发式决定，任何带扩展名的无 scheme 串（如 `example.com/a.js`、`docs/x.md`）都会先被判成 'file'，永远不会走到外链分支，语义上无法区分“本地相对路径”与“省略协议的外部主机”。建议让 linkTarget 直接返回解析结果（如 `{kind,ref}`），把 file 判定收敛为明确的相对路径规则。
- 判断：文件引用有意采用受限相对路径+行号启发式，不承诺冒号文件名或无scheme外链。HTTPS外链仍正确识别，重复纯解析不是现存缺陷。
- 证据：frontend/src/content.ts:9

## OCR-2229 · 不采纳

- 位置：frontend/src/upstream/columns.ts:39；OCR原严重度：medium
- 计划：无
- 原意见：非有限数值未校验：当 px 为 NaN 时，Math.round(NaN) → NaN，后续 Math.max/Math.min 会继续传播 NaN，clampWidth 返回 NaN 而不是契约范围内的宽度；若 px 为 Infinity 则被静默截为 max。该函数是导出的公共几何工具，持久化偏好值（localStorage/旧配置）或未完成测量的 viewport 都可能传入 NaN，最终渲染出无效的 `NaNpx` 样式并破坏布局。建议在舍入前做有限性归一化。
- 判断：clampWidth仅由App状态有限常量和PointerEvent.clientX/键盘增量调用，不从localStorage恢复宽度；当前值均有限。NaN是假设无效调用，不能为未出现偏好持久化改来源。
- 证据：frontend/src/upstream/columns.ts:39

## OCR-2230 · 不采纳

- 位置：frontend/src/upstream/columns.ts:53；OCR原严重度：medium
- 计划：无
- 原意见：collapsedWidth 完全绕过了 SIDEBAR_MIN/MAX 校准：传入负值会得到负的 sidebar 宽度，传入大于 viewport 的值会让侧栏超出画面（center 被 Math.max(0, …) 截为 0，但 sidebar 仍溢出）。由于该参数来自调用方配置（如 macOS 桌面传 0、其他传 56），一旦配置值异常或 viewport 测量异常就无法自愈。建议对 collapsedWidth 做非负且不超过 viewport 的归一化，非有限时回退到 SIDEBAR_COLLAPSED。
- 判断：App只导入clampWidth与常量，不调用computeColumns；其文档明确normal geometry sidebar never concedes，上游未来Desktop布局不属当前Web。任意负collapsedWidth/264以下viewport假设未进入现有运行。
- 证据：frontend/src/upstream/columns.ts:53

## OCR-2231 · 不采纳

- 位置：frontend/src/upstream/columns.ts:58；OCR原严重度：medium
- 计划：无
- 原意见：窄视口下三列宽度之和可能大于 viewport：sidebar 最终按 SIDEBAR_MIN=264 收敛（或采用未经校准的 collapsedWidth），当 viewport 小于该宽度时 center 会被截为 0，但 sidebar 本身仍超出画面，返回值的 `sidebar + center + rightbar` 不再等于 frame 宽度，违反“为一帧求解列宽”的契约。这正是文中提到的 narrowExpanded（<1024 手动展开侧栏）路径容易触发的场景。建议在返回前把 sidebar 收敛到 [0, viewport]（必要时同时收敛 rightbar），并保证 center 非负；同时对 viewport 做有限性与非负校验，避免 NaN 直接进入三个列宽。
- 判断：App只导入clampWidth与常量，不调用computeColumns；其文档明确normal geometry sidebar never concedes，上游未来Desktop布局不属当前Web。任意负collapsedWidth/264以下viewport假设未进入现有运行。
- 证据：frontend/src/upstream/columns.ts:58

## OCR-2232 · 部分采纳

- 位置：sdk/tests/client.ts:5；OCR原严重度：high
- 计划：R0-03 / P3
- 原意见：环境变量通过非空断言 `!` 直接读取，运行时若 `JHARNESS_API_KEY` / `JHARNESS_SDK_WORKSPACE` 未设置会得到 `undefined`。当 `secret` 为 `undefined` 时，后续 `JSON.stringify(...).includes(secret)` 与 `spawnargs.join(' ').includes(secret)` 会退化为匹配字符串 "undefined"，使所有脱敏断言静默通过（假阴性），无法证明密钥未泄漏。建议在文件开头显式校验这些必填环境变量，缺失时抛出明确错误。
- 判断：TS测试非空断言只静态，API key缺失时includes(undefined)确不能检秘密；上层run.mjs已显式注入synthetic，单独运行仍应前置校验并拒缺项，而非默认为干净。
- 证据：sdk/tests/client.ts:5

## OCR-2233 · 部分采纳

- 位置：sdk/tests/client.ts:13；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：这几处测试都只断言了错误/关闭调用，却没有真正验证子进程是否被终止，标签描述超出实际校验范围：(1) c-15 'version mismatch rejects without execution and closes child' 只校验 `VERSION_UNSUPPORTED`，未验证未拉起进程或子进程已被清理；(2) c-19 'all owned children closed' 在循环中只检查最后一次赋给 client 的实例，重启前的进程是否退出、各场景 close() 是否生效都未验证；(3) c-22 'response fails closed and stops owned peer' 只断言 `INVALID_RESPONSE`，close() 后未检查进程确实终止。建议：在每次 close() 后断言 `exitCode`/`signalCode` 非空（必要时带超时轮询），或收集所有启动过的 pid 统一校验；若无法验证，应将标签改为只描述实际验证的内容，避免误导。
- 判断：初始/末个进程有exitCode检查，但版本拒绝、各中间重启和故障peer未逐次证明关闭；采纳每次close后自有ChildProcess终止断言与完整实例列表，禁按历史PID探杀。
- 证据：sdk/tests/client.ts:13

## OCR-2234 · 部分采纳

- 位置：sdk/tests/client.ts:26；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：这里只断言抛出的是 `RpcError`，任意 RPC 错误（如超时、进程断开、协议错误）都会让该用例通过，无法确认确实是“callId 不匹配”导致的拒绝。建议断言具体错误码（如 `INVALID_APPROVAL` / `INVALID_PARAMS`），与文件中其它精确错误码断言保持一致。
- 判断：wrong callId只要求RpcError，可误接超时/断线；补实际STALE_APPROVAL等服务契约错误码以及写前文件不存在，不能照OCR猜INVALID_PARAMS。
- 证据：sdk/tests/client.ts:26

## OCR-2235 · 部分采纳

- 位置：sdk/tests/client.ts:36；OCR原严重度：low
- 计划：R0-03 / P3
- 原意见：若不存在带 `artifactRef` 的 `tool.completed` 事件，`find` 返回 `undefined`，随后 `artifact.artifactRef` 会抛 `TypeError` 而不是清晰的断言失败，失败原因难以定位。建议先用 `assert.ok(artifact, 'no artifactRef in tool.completed events')` 做显式断言。
- 判断：artifact find缺项会TypeError并非误PASS；补必需artifact存在与来源事件断言提高定位，继续保持真实产物摘要/范围校验。
- 证据：sdk/tests/client.ts:36

## OCR-2236 · 不采纳

- 位置：sdk/tests/client.ts:40；OCR原严重度：high
- 计划：无
- 原意见：`pid` 声明后从未使用（死代码）。同时这里只检查 `spawnargs`，并未校验子进程的环境变量白名单，密钥完全可能通过 `env` 泄漏而测试仍然通过；若 `secret` 为 `undefined` 还会退化为匹配 "undefined"。建议删除未使用的 `pid`，并显式断言子进程环境（启动时传入的 env 或 `/proc/<pid>/environ`）不含 `JHARNESS_API_KEY`。
- 判断：测试宿主/SDK本身是受信任被测本机服务，继承运行环境不等同工具执行的白名单绕过；该文件显式覆写合成fixture凭据。未提供真实无关变量被记录或外发证据，不能把继承环境本身判为已发生泄漏。
- 证据：sdk/tests/client.ts:40

## OCR-2237 · 采纳

- 位置：sdk/tests/client.ts:59；OCR原严重度：high
- 计划：R0-03 / P2
- 原意见：`client.call('stderr')` 的返回值被直接丢弃，`check()` 未传 value 默认 true，因此该断言恒真，无法发现子进程诊断文本泄漏到 API 结果中。建议捕获返回值并断言其中不含 stderr 文本，例如 `const res=await client.call('stderr'); check('...', !JSON.stringify(res).includes('<诊断标记>'))`。
- 判断：stderr调用结果完全丢弃且check默认true，确不能证结果无诊断secret；应记录返回值并断缺合成secret，同时保持读取管道正常。
- 证据：sdk/tests/client.ts:59

## OCR-2238 · 部分采纳

- 位置：sdk/tests/client.ts:58；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：用固定 `wait(900)` 假设服务端已处理完 `slow` 调用，在慢速 CI / 慢 JVM 上 900ms 可能不足，导致 `slowCalls` 仍为 0 而出现误报失败（不可复现）。建议改为用 `until` 轮询直到 `stats.slowCalls===1`（或超时），或让 peer 暴露“迟到响应已到达”的可查询状态。
- 判断：peer收到slow时立刻increment，随后虚线程延迟700ms，stats按stdin顺序本就见slowCalls=1；OCR称900ms不足令count=0错误。真正缺口是900ms不能可靠证明迟到回复已送达，采纳显式lateReplySent状态验证而非仅轮询count。
- 证据：sdk/tests/client.ts:58

## OCR-2239 · 不采纳

- 位置：sdk/typescript/src/index.ts:41；OCR原严重度：high
- 计划：无
- 原意见：子进程默认继承父进程**全部**环境变量（`options.env ?? process.env`）。SDK 调用方进程里通常还有云厂商凭据、GitHub token、代理口令等与 J-Harness 无关的机密，这些都会出现在 Java 子进程的 `/proc/<pid>/environ` 中，并可被插件、工具调用或诊断/崩溃上报读取，等于把无关机密扩大了暴露面。建议默认只透传最小白名单（PATH、HOME、LANG/LC_ALL、TMPDIR、JAVA_HOME、Windows 下的 SystemRoot/TEMP/TMP 等），必需的 harness 凭据（例如访问本机产品 API 的 token）由调用方通过 `options.env` 显式传入，而不是整包继承。
- 判断：SDK按调用者环境或整体env替换启动自有Java是契约，模型key和凭据服务本就依赖该环境；不是不可信命令工具的环境白名单边界。默认剥离会破坏apiKeyEnv，调用方可显式env隔离。
- 证据：sdk/typescript/src/index.ts:41

## OCR-2240 · 部分采纳

- 位置：sdk/typescript/src/index.ts:78；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：`expired` 只在最近 64 个超时 id 中保留墓碑，一旦被淘汰，对应的迟到响应会落到下一行 `pending.get` 为空的分支并 `throw`，被外层 `catch`（第 86 行）当成 `INVALID_RESPONSE`，直接 `fail()` + `kill()`，把整条连接和所有在途请求一起杀死。触发条件：连续发生 65 次以上请求超时后，任意一个较早超时请求的响应才到达（网络/服务端慢响应即可复现）。同理，服务端重复回包或任何未知 id 的合法帧也会终结整个连接——一个协议细节不该升级为连接级故障。建议：未知/已淘汰 id 只丢弃并计数（必要时通过事件暴露），不要 fail/kill；墓碑应是带上限的 LRU 并延长保留期，只有帧解析失败、字段结构非法这类真正的协议破坏才 kill。
- 判断：expired墓碑64项淘汰后迟到响应被判未知杀连接属实；真正未知/重复id fail-closed合理，不能一律忽略。采纳区别已知过期、明确淘汰策略及65次延迟响应回归。
- 证据：sdk/typescript/src/index.ts:78

## OCR-2241 · 采纳

- 位置：sdk/typescript/src/index.ts:96；OCR原严重度：high
- 计划：R3-04 / P1
- 原意见：`kill()` 只发送默认的 SIGTERM，没有 SIGKILL 升级：若子进程（或它的 JVM shutdown/插件）忽略 SIGTERM，最多等 2 秒后本函数返回，但子进程仍在运行，成为孤儿进程并继续占用 workspace/端口/凭据。更糟的是并发 close 的语义：第 91 行 `if(this.closing)return this.exit` 返回的是裸 `this.exit`（`close` 事件才 resolve），既没有超时也没有 SIGKILL 兜底，第一个调用方在 2 秒后放弃时，并发调用方会**永久挂起**（并一直持有事件循环/调用栈）。触发条件：子进程挂死或忽略 SIGTERM，同时有两个以上调用方 await close()。建议：第一个调用方持有唯一的 `closingPromise`，所有调用方都 await 这个带超时的 promise；超时后执行 `this.process.kill('SIGKILL')` 并再次有界等待。
- 判断：TS close只SIGTERM等2秒返回，并发close返回裸exit无上限；忽略SIGTERM可令并发调用永挂。需共享有界closingPromise、SIGKILL升级和回收。
- 证据：sdk/typescript/src/index.ts:96

## OCR-2242 · 部分采纳

- 位置：sdk/typescript/src/index.ts:99；OCR原严重度：high
- 计划：R3-04 / P2
- 原意见：`session.open` 没有可对账的幂等标识：超时后 reject 的是 `REQUEST_TIMEOUT_ACCEPTANCE_UNKNOWN`，而此时子进程可能已经注册了 handle，但响应丢失，调用方既拿不到 `handle`（后续 `sendInput`/`cancel` 等必需参数），也无法确认是否已创建；重试必然分配新的 handle，泄漏旧 handle 并消耗服务端上限（`HANDLE_LIMIT`=32），直到子进程重启才释放。这与类注释“retain requestId and reconcile explicitly”不一致，且属于 UNKNOWN 状态不可安全重放的问题（对比 `sendInput`/`answer` 都接受 requestId）。建议：为 openSession 增加调用方提供的幂等键（需服务端在 `session.open` 接受并按其去重、或在超时后支持按该键查询已分配 handle），或在超时后提供查询 handle 的对账接口；在补齐前至少要在文档/类型上标注该调用不可安全重试。
- 判断：session.open草稿句柄无对账id，丢响应重开消耗32配额；它不运行工具/模型，非执行重放。采纳可查句柄或明确重启释放的恢复说明。
- 证据：sdk/typescript/src/index.ts:99

## OCR-2243 · 部分采纳

- 位置：sdk/typescript/src/index.ts:0；OCR原严重度：medium
- 计划：R3-04 / P2
- 原意见：两处同步异常会绕过 Promise 语义：1) `JSON.stringify` 在 `new Promise` 之前执行，params 含 BigInt、循环引用或抛错的 getter 时会同步 throw，`call()` 的调用方（习惯写 `.catch`/`await`）会得到同步异常，异常向上冒泡时可能连带把整个调用栈打挂；2) 第 65 行 `stdin.write` 若同步 throw（流已 destroyed、已 end、参数非法），executor 抛错使 Promise 直接 reject，但第 64 行已注册的 `pending` 条目与定时器不会被清理——该 id 会一直占据 `pending` 配额（≥32 后所有请求返回 BACKPRESSURE_LIMIT），并且定时器未 unref，会让事件循环空转最长 `timeoutMs`，之后才进入 `expired`。建议：序列化单独 try/catch 并返回 rejected Promise；write 也用 try/catch，失败时 `clearTimeout` + `pending.delete` + reject，或直接走 `fail()`。
- 判断：call在Promise前JSON.stringify，JS调用者BigInt/循环可同步抛，write异常也须清pending/timer。采纳统一INVALID_REQUEST拒绝和清理，不自动重试。
- 证据：sdk/typescript/src/index.ts:60

## OCR-2244 · 不采纳

- 位置：sdk/typescript/src/index.ts:52；OCR原严重度：medium
- 计划：无
- 原意见：协议协商与容量上限被硬编码，和 `initialize` 返回的能力集脱节：`options.protocolVersion` 可配置，但这里只接受字面量 `'1.0'`，传入其它值即使对端按请求版本协商成功也会被判 `VERSION_UNSUPPORTED`（服务端当前也仅支持 1.0，因此该选项实际上是“可配但不可用”的陷阱）。同时 `initialize` 结果中的 `maxPending`/`maxRequestBytes`/`maxResponseBytes`（服务端在 StdioSdkServer 的 initialize 中明确返回）被完全忽略，代码里写死 32/65536/524288；服务端调小 `maxPending` 或调大响应上限时，SDK 会继续按旧上限发送/判定，出现静默的协议不一致（例如请求被服务端拒绝、或合法的较大响应被判 INVALID_RESPONSE 导致断连）。建议：从 capabilities 读取并校验上限，协议版本按“客户端支持集合 ∩ 服务端返回”判断，否则移除该选项以免误用。
- 判断：客户端仅支持协议1.0，传不支持版本用于拒绝测试；固定上限是1.0客户端自身预算，不能因未来1.1被拒认兼容缺陷，也不能盲信服务端扩大上限。
- 证据：sdk/typescript/src/index.ts:52

## OCR-2245 · 部分采纳

- 位置：frontend/tests/client.test.ts:218；OCR原严重度：medium
- 计划：R0-03 / P3
- 原意见：第 218 行只证明了 sessionId 替换会被拒绝，测试名承诺的“source 替换”没有任何断言，也没有断言第 216 行请求的 URL 只携带 kind/artifactId/eventId 且为 GET、无 body。如果客户端把 source 直接透传，或允许 content/page 之外的 source，规范化/白名单校验被绕过时该用例仍会通过。建议补一个 `body={...sample,source:'raw-log'}` 的拒绝用例，并断言 `calls[0].path` 的查询参数。
- 判断：用例已有GET且body undefined断言，OCR此子主张误报；缺source替换负例属实，补raw-log/跨kind source拒绝而不重复现有GET断言。
- 证据：frontend/tests/client.test.ts:218

## OCR-2246 · 不采纳

- 位置：frontend/tests/client.test.ts:199；OCR原严重度：medium
- 计划：无
- 原意见：文件系统边界用例只覆盖了明文 `../`、绝对路径、盘符与伪协议，缺少百分号编码穿越（`%2e%2e/`、`%2f`）、反斜杠（`..\secret`）、NUL（`%00`）、超长路径和 CRLF。当前 `fileReference('%2e%2e/secret.txt')` 会原样返回 `{path:'%2e%2e/secret.txt'}`，`linkTarget` 也会判为 `'file'`；一旦服务端对 path 再做一次 URL 解码就会形成目录穿越。触发条件：用户点击内容中形如 `%2e%2e/secret.txt` 的链接。建议补充这些否定用例并断言 `fileReference` 抛错/`linkTarget` 返回 `'blocked'`。
- 判断：路径通过UTF8 base64url传后端，服务端再按真实工作区检查，不会对%2e作URL二次解码；%字面文件名不必禁止。现有反斜杠会归一后拒..，不因假设未来decode扩展拒合法字面路径。
- 证据：frontend/tests/client.test.ts:199

## OCR-2247 · 部分采纳

- 位置：frontend/tests/client.test.ts:94；OCR原严重度：high
- 计划：R0-03 / P3
- 原意见：SSE 用例只构造了以 LF（`\n\n`）分隔、单条 `data:` 的帧，且仅做了一次 32 字节切分。实现按 `'\n\n'` 查找帧边界：若服务端使用 CRLF（`\r\n\r\n`）或同一事件包含多条 `data:` 行，帧将永远不闭合并在流结束时被整体丢弃，而本用例依旧通过。建议补充 CRLF 帧、多行 `data:`（规范要求以 `\n` 拼接）、注释行（`: keep-alive`）以及跨 UTF-8 多字节字符切分的用例，并断言重连时游标推进。
- 判断：当前SSE端点生产者固定LF单data且客户端不是通用第三方EventSource，缺CRLF/多data支持未破当前契约；采纳跨UTF8字节/任意chunk切分与注释/截断重连测试，不强加新SSE输入方兼容。
- 证据：frontend/tests/client.test.ts:94

## OCR-2248 · 采纳

- 位置：frontend/tests/client.test.ts:103；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：第 103 行只断言长度等于 4000，没有锁定保留的是哪一端。当前实现先按 eventId 去重再 `slice(0, 4000)`，会丢弃最新到达的 100 个事件（`bounded_4000`..`bounded_4099`），断言行仍然通过。若语义要求保留最新事件，这就是真实的数据丢失缺陷。建议断言首尾 eventId（例如包含 `bounded_4099`、不含 `bounded_0`），或显式断言保留最早 4000 条并加注释说明该截断方向。
- 判断：merge测试只验证4000长度，不锁截断方向；与2251合并，先明确有限历史窗口与最新进度策略，再断首尾ID/游标及回读行为，不能先假定slice(-4000)唯一正确。
- 证据：frontend/tests/client.test.ts:103

## OCR-2249 · 部分采纳

- 位置：frontend/tests/client.test.ts:185；OCR原严重度：medium
- 计划：R0-03 / P2
- 原意见：第 185 行只证明 answerQuestion 只发一次 POST、随后用 GET 对账，但没有断言 POST body，测试名声称的幂等/不重放安全性并未真正被证明：若 `requestId`、`turnId`、`callId`、`sessionId` 从 body 中丢失，服务端无法去重或会直接拒绝，该用例仍然通过。建议在 fetcher 中记录并断言 body 为 `{questionId,sessionId,turnId,callId,requestId:'request1',selected:['a'],text:'',skip:false}`。第 136 行 submitTurn 的 `requestId` 同样只断言了 method+URL，建议一并断言 body。
- 判断：丢回答/续轮测试只断method+URL未断body，不能证明关联与requestId精确载荷；补完整request JSON和一次POST后的GET对账，保持不自动重试。
- 证据：frontend/tests/client.test.ts:185

## OCR-2250 · 部分采纳

- 位置：frontend/tests/client.test.ts:52；OCR原严重度：low
- 计划：R0-03 / P2
- 原意见：用例标题是 “explicit workspace filters”，但第 52 行只断言了 `profile=default`。`taskPage('default','archived')` 传入的 `view=archived`、`limit=32`，以及第二次调用应携带的 `after` 游标都没有校验。若实现把 view 拼错或忽略 after 游标，会跨状态/跨页返回错误数据而测试依然通过。建议分别断言路径包含 `view=archived`、`limit=32`，并在带 cursor 的调用中断言 `after=<cursor>`。
- 判断：taskPage只检查profile而未锁view/limit/after，与explicit filters名称覆盖有差；补archived/分页cursor准确查询及新旧页不混合。
- 证据：frontend/tests/client.test.ts:52

## OCR-2251 · 部分采纳

- 位置：frontend/src/api.ts:114；OCR原严重度：high
- 计划：R4-03 / P1
- 原意见：`mergeEvents` 拼接后执行 `.slice(0, 4000)`，保留的是**最旧的** 4000 条，一旦缓冲区达到上限，后续通过 `stream`/分页追加的新事件会被静默丢弃（`App.tsx` 中 `setEvents(old => mergeEvents(old, [e]))` 直接受影响），界面实时状态停止更新，而事件游标仍在前进，用户看不到最新进度。应保留最新的一段。
- 判断：mergeEvents确实保留最旧4000项；App还有历史上限/截断行为，不能简单slice(-4000)破坏分页。采纳明确事件窗口语义，达到上限仍展示新进度并保留历史回读，补4001条边界验证。
- 证据：frontend/src/api.ts:114

## OCR-2252 · 部分采纳

- 位置：frontend/src/api.ts:288；OCR原严重度：high
- 计划：R4-01 / P2
- 原意见：`readJson` 只在校验成功路径上检查 `signal?.aborted` / `expired`；当 12 秒超时触发 `reader.cancel()` 或外部 AbortSignal 中断时，`reader.read()` 会以 `AbortError` 拒绝，直接落入此 catch，被归类为 `INVALID_RESPONSE`（服务端数据格式错误），调用方无法区分“响应体超时/被取消”与“协议违例”，也就无法按取消语义处理；同时 `expired` 的成功路径检查实际不可达（超时必然先让 read 抛错）。请在 catch 中优先判断 `signal?.aborted`（ABORTED）与 `expired`（CONNECTION_LOST），仅在两者都不成立时才报 `INVALID_RESPONSE`。
- 判断：readJson catch不优先signal/expired，底层流错误可能被归INVALID_RESPONSE；但reader.cancel通常使done=true，成功路径expired检查并非必不可达。采纳统一取消/超时归类并分别模拟done与reject路径。
- 证据：frontend/src/api.ts:288

## OCR-2253 · 不采纳

- 位置：frontend/src/api.ts:331；OCR原严重度：high
- 计划：无
- 原意见：`stream` 的 10 秒定时器只调用 `abort()` 取消 reader，但没有像 `readJson` 那样设置 `expired` 标记：若取消导致 `read()` 以 `done: true` 结束，循环将正常退出，函数**静默返回**，末尾未提交的残余帧被丢弃且调用方无法感知截断；若 `read()` 以 AbortError 抛错，则被下面的 catch 归为 `CONNECTION_LOST`（同样的超时产生两种不同结果）。此外该定时器是固定总时长而非空闲超时，事件持续到达但连接超过 10 秒也会被强制中断。建议引入 `expired` 标记并在结束/异常时统一抛出可识别错误（或明确返回 truncated 标志），并在收到数据时重置定时器。
- 判断：stream总10秒与有界SSE连接、丢最后不完整帧/用最后eventId重连是源码明确设计，App读生命周期重新建立连接；不是要求无限空闲流。改为每chunk重置会破总时长界限。可记录结束原因诊断，但不认正常边界为缺陷。
- 证据：frontend/src/api.ts:331

## OCR-2254 · 不采纳

- 位置：frontend/src/api.ts:97；OCR原严重度：medium
- 计划：无
- 原意见：`task()` 把 `modelId` 限制为 32 个字符，而 `ModelConnection.id`（`identifier`）与 `SettingsData.selected` 允许到 128 个字符，`/v1/models/check`、`/v1/tasks` 提交也直接用 `identifier(modelId)`。当服务端返回的合法 model id 长度在 33~128 之间时，整个任务对象会被判为 `INVALID_RESPONSE`，任务列表/详情直接报错隐藏。两侧上限应统一（这里至少应使用与模型 id 一致的 128）。
- 判断：WorkbenchSettings保存和validate都field(id,32)，HarnessWebServer模型选择text32；合法生产modelId不会33-128。通用identifier128较宽不代表模型契约128，不能放宽task以掩盖无效服务端输入。
- 证据：frontend/src/api.ts:97

## OCR-2255 · 部分采纳

- 位置：frontend/src/api.ts:162；OCR原严重度：medium
- 计划：R4-01 / P3
- 原意见：`offset` 未做整数/范围校验就拼接进查询串（`questionPage` 已做 `Number.isInteger(offset) && 0 <= offset <= 64` 的同类校验）。传入 `NaN`、小数、负数或形如 `0&limit=1000` 的字符串会被原样写入 URL，导致请求路径畸形或注入额外查询参数；建议与 `questionPage` 保持一致做严格校验。
- 判断：turns offset确直接插URL，当前调用仅受控0/32分页数字，服务端仍校范围；采纳输入安全整数/配额校验，JS绕类型值不得注入额外query。不是用户可达权限绕过。
- 证据：frontend/src/api.ts:162

## OCR-2256 · 不采纳

- 位置：frontend/src/api.ts:72；OCR原严重度：medium
- 计划：无
- 原意见：草稿校验要求 `draft.selected` 存在时 `draft.text` 也必须是字符串：若服务端只回传已选择的选项而没有 `text` 字段（空文本被省略是常见实现），`text(draft.text,4096)` 会抛 `INVALID_RESPONSE`，整条用户提问解析失败，草稿恢复功能不可用。`text` 应按可选字段处理并缺省为空串。
- 判断：userQuestion草稿契约成对selected/text；后端questionDraft固定返回text，省略空text只是OCR假设，不能放松当前严格响应校验。
- 证据：frontend/src/api.ts:72

## OCR-2257 · 部分采纳

- 位置：frontend/src/api.ts:174；OCR原严重度：low
- 计划：R4-01 / P3
- 原意见：`input.limit` 被用作结果条数上界（`v.matches.length > input.limit`），但调用方传入的 `limit` 未做整数/范围校验：当 `limit` 为 `NaN` 时比较恒为 false，条数上界检查被绕过；为负数时又会让所有响应都判为非法。应在发请求前校验 `limit` 为 1~上限的整数（并在请求体中只发送校验后的值），以免边界校验失效。
- 判断：searchContent limit在本地用于响应上限但未先验证整数1-32，当前UI固定20且服务器会拒非法JSON值。采纳调用边界校验以使NaN不能削弱客户端结果预算，不认当前UI已无界。
- 证据：frontend/src/api.ts:174

## OCR-2258 · 采纳

- 位置：frontend/src/ModelPicker.tsx:11；OCR原严重度：high
- 计划：R4-01 / P2
- 原意见：effect 只在 `api` 变化时执行，却在闭包里读取 `value`/`onChange`：App 中 `modelId` 初始为 `''`（App.tsx:99），若用户在 `/v1/models` 返回前手选了一个模型，返回后闭包里的 `value` 仍是 `''`，会执行 `onChange(v.selected)` 把用户刚做的选择覆盖掉。反过来，外部把 `value` 改回 `''`（Settings 关闭时 `setModelId('')`，App.tsx:303）不会触发该 effect，导致 `value` 与任何 `<option>` 都不匹配、下拉框显示空白，提交时静默回落到服务端默认模型。建议不要用 effect 回写父状态，而是在渲染时派生选中值（如 `models.some(m => m.id === value) ? value : models[0]?.id ?? ''`），或用只依赖 `models` 的兜底 effect 并避免重复拉取。
- 判断：ModelPicker effect只依赖api但读取启动时value，Settings关闭后setModelId空串也不会重新选择。采用最新value与请求代次，明确刷新后的可用选择。
- 证据：frontend/src/ModelPicker.tsx:11

## OCR-2259 · 部分采纳

- 位置：frontend/src/ModelPicker.tsx:9；OCR原严重度：medium
- 计划：R4-01 / P2
- 原意见：`v.selected` 未校验是否存在于 `v.models` 中就直接传给 `onChange`。`Api.models()` 只保证 `selected` 是合法 identifier，并不保证它出现在 `models` 里；一旦后端返回不一致数据，受控 `select` 的 `value` 会与所有 `<option>` 都不匹配（界面空白），并把一个不可用的模型 id 通过 `api.submit` 发给服务端（UNKNOWN_MODEL）。建议先做 `const next = v.models.some(m => m.id === v.selected) ? v.selected : v.models[0].id` 再回填（`settings()` 已保证 `models.length >= 1`）。
- 判断：models的selected未在客户端验证集合成员；正常服务端应一致，尚非已证实正常响应错误。采纳边界成员校验和无匹配时显式错误。
- 证据：frontend/src/ModelPicker.tsx:9

## OCR-2260 · 部分采纳

- 位置：frontend/src/ModelPicker.tsx:7；OCR原严重度：medium
- 计划：R4-01 / P2
- 原意见：重新拉取时没有清空旧状态：`api` 重建（如 401 后重连，App.tsx:218-220 会 `setApi(candidate)`）后，如果新的 `api.models()` 失败，这里只会把 `error` 置 true，上一个会话/凭据下的 `models` 仍保留，下拉框依旧可用，用户可以把已失效的连接选中并提交。建议在 effect 开始时 `setModels([])` 并重置 `error`（或用请求序号/`api` 标识丢弃过期响应），使「读取失败」时不再展示旧连接。
- 判断：失败只setError不清models，无models时空option和非空受控value失配。采纳清旧列表/正确占位；App常规重连会重挂载，风险限同实例复用。
- 证据：frontend/src/ModelPicker.tsx:7

## OCR-2261 · 部分采纳

- 位置：frontend/src/ModelPicker.tsx:15；OCR原严重度：medium
- 计划：R4-01 / P2
- 原意见：当 `models` 为空但 `value` 非空（例如 `/v1/models` 失败、`value` 仍保留上次的模型 id）时，这个唯一的 `<option value="">` 无法与受控 `select` 的 `value` 匹配，浏览器会显示空白，「无法读取连接…」/「正在读取连接…」提示实际不可见；同时 select 因 `!models.length` 被禁用，用户既看不到错误也没有重试入口。建议占位 option 使用当前 `value`，并在错误态下提供重试（重试而不是永久禁用）。
- 判断：失败只setError不清models，无models时空option和非空受控value失配。采纳清旧列表/正确占位；App常规重连会重挂载，风险限同实例复用。
- 证据：frontend/src/ModelPicker.tsx:15

## OCR-2262 · 采纳

- 位置：frontend/src/Markdown.tsx:8；OCR原严重度：medium
- 计划：R4-03 / P3
- 原意见：notice 生命周期存在问题：复制成功后，若父组件传入新的 text（该组件在流式/刷新场景下会持续重渲染），notice 仍显示“已复制”；另外多次点击复制时先返回的旧 Promise 可能覆盖后一次的结果。由于块级 key 使用行号（`key={index}`），文本变化导 keys 位移时，同一个 CodeBlock 实例可能复用到另一段代码上，notice 会误挂到别的代码块。建议在 text 变化时重置 notice（例如 `useEffect(()=>setNotice(''),[text])` 或对 CodeBlock 使用 `key={language+text}`），并用递增序号/取消标记丢弃过期结果。
- 判断：CodeBlock notice不随text重置且copy Promise无代次，按行号key复用后旧复制提示可能挂到新代码上。采纳文本变更重置及异步结果代次。
- 证据：frontend/src/Markdown.tsx:8

## OCR-2263 · 部分采纳

- 位置：frontend/src/Markdown.tsx:9；OCR原严重度：medium
- 计划：R4-03 / P3
- 原意见：复制分支的错误处理不完整：特性检测只判断 `navigator.clipboard` 是否存在，未校验 `writeText`。在非安全上下文（http 页面）或权限受限的实现中，`navigator.clipboard.writeText` 可能为 undefined，此时调用会同步抛出 TypeError，`.then/.catch` 链完全不会执行，用户既看不到“复制失败”提示，异常还会冒泡到全局。建议先校验 `typeof navigator.clipboard?.writeText === 'function'`，并用 try/catch（或 async 函数 + await）包住同步调用。
- 判断：只判断clipboard对象不判断writeText，缺方法实现可同步抛出跳过链式catch；localhost标准实现通常完整。采纳函数检测和统一try/catch。
- 证据：frontend/src/Markdown.tsx:9

## OCR-2264 · 采纳

- 位置：frontend/src/Markdown.tsx:34；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：列表解析只看首行就确定 ol/ul，随后的 while 循环却无条件吞掉所有匹配 `-`/`*`/`1.` 的行。因此 `- a` 后紧跟 `1. b`（或反向）会被渲染成单一列表，第二项的有序/无序语义被静默丢弃；同时有序列表也不记录起始序号，`3.` 开头会显示为 1。建议循环内部按每行的实际标记分组，遇到类型变化就结束当前列表并重新进入外层循环，同时用 `start` 保留有序列表首项序号。
- 判断：列表循环吞入混合有序无序行，仅第一行决定ol/ul，且不保留ol起始号。应按标记分组并保留start。
- 证据：frontend/src/Markdown.tsx:34

## OCR-2265 · 不采纳

- 位置：frontend/src/Markdown.tsx:21；OCR原严重度：low
- 计划：无
- 原意见：这里在 false 分支里再次嵌套三元表达式（`kind==='file'&&onFile&&ref ? ... : ...`），违反项目规则中“禁止嵌套三元表达式”的要求，且可读性差。建议抽成一个渲染函数（如 `renderLink(kind, label, target, ref, onFile)`），用 if/else 分支返回对应元素。
- 判断：嵌套三元、!=null和防御性冗余分支不自动构成运行缺陷；OCR风格规则不能替代仓库契约。上游原样移植代码还须保留来源，不能为风格批改。
- 证据：frontend/src/Markdown.tsx:21

## OCR-2266 · 部分采纳

- 位置：frontend/src/Markdown.tsx:27；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：这里只限制了字符数（64K），没有限制生成的块级/行内节点数：形如 `- a\n` 的输入约 1.6 万行即可生成上万个 `<li>` 和同等数量的 `Inline` 调用，每次渲染还要为每行重新构造正则对象（`Inline` 内的 pattern 在函数内编译），可能造成主线程长任务与交互卡顿；CodeBlock 有 2000 行兜底，而块级解析没有类似上限。建议对 blocks/Incomplete 节点数设置上限（超过后返回“仅展示部分内容”提示），并把 Inline 的正则提升到模块级复用。
- 判断：65536字符上限仍允许上万短列表节点，块/节点无总预算；非无限输入。采纳节点预算/截断及最大合法文本主线程响应测量。
- 证据：frontend/src/Markdown.tsx:27

## OCR-2267 · 不采纳

- 位置：frontend/src/Conversation.tsx:43；OCR原严重度：high
- 计划：无
- 原意见：工具卡片将 `request.payload`（调用参数，可能含命令、环境变量、token/密钥）与 `result.payload`（原始输出）直接 `pretty()` 后渲染进 DOM，未做任何脱敏。注意 `<details>` 的子树在折叠状态下也会挂载，因此敏感内容无需用户展开就已进入 DOM/可通过读屏器、DevTools 或复制页面获取。建议在渲染前统一走脱敏函数（对 `token`/`secret`/`password`/`apiKey`/`authorization`/`env` 等键名做掩码，长字符串截断），或改为仅在展开时按需拉取已脱敏内容（与 ContentReader 一致的脱敏策略）。
- 判断：Conversation读取产品已脱敏的事件payload；脱敏责任在事件/持久服务，仅凭组件无二次键名过滤不能证明泄漏。按token/env键随意掩码会损失真实工具事实。
- 证据：frontend/src/Conversation.tsx:43

## OCR-2268 · 部分采纳

- 位置：frontend/src/Conversation.tsx:40；OCR原严重度：medium
- 计划：R4-03 / P3
- 原意见：`pretty(r)` 在每次渲染时都会把整个 `result.payload.value` 序列化成完整字符串，随后才用 `body.slice(0,4000)` 截断展示，超大工具输出（例如整份文件内容、大日志）会造成整串序列化 + 常驻内存，且 `<details>` 折叠时同样会渲染。建议在序列化阶段就限长（保留 4001 字符即可维持 `body.length>4000` 的截断判断语义），并对 `changes.map` 也加上数量上限（如只渲染前 N 个并提示剩余数量），满足有界数据处理要求。
- 判断：ToolCard先序列化全value再截4000，折叠details也构造；API已有响应上限，非无界。采纳惰性展开与预览预算以减少上限内峰值。
- 证据：frontend/src/Conversation.tsx:40

## OCR-2269 · 不采纳

- 位置：frontend/src/Conversation.tsx:0；OCR原严重度：high
- 计划：无
- 原意见：结束摘要先判断 `status==='COMPLETED'` 就直接宣称“记录的证据已确认目标完成”，而 `reason` 只在非 COMPLETED 时才被检查。当 `turn.ended` 同时为 COMPLETED 且 `reason` 以 `GOAL_UNVERIFIED` 开头时，界面会把“目标未验证完成”错误地显示为“已确认目标完成”，属于协议/状态语义上的过度断言。建议先判断 `GOAL_UNVERIFIED`（以及其它 UNKNOWN/未验证原因），再回落到 COMPLETED 的文案。
- 判断：EvidenceCompletion.no(GOAL_UNVERIFIED)返回achieved=false，DefaultAgentLoop:317由此设FAILED，不会产COMPLETED+GOAL_UNVERIFIED；OCR构造矛盾组合不在生产事件路径。
- 证据：frontend/src/Conversation.tsx:72

## OCR-2270 · 采纳

- 位置：frontend/src/Conversation.tsx:65；OCR原严重度：low
- 计划：R4-01 / P2
- 原意见：`value` 状态为 PERSISTED/IDLE/UNAVAILABLE 时直接 `return null`，会同时吞掉 `error`——一旦曾经取到过 value，后续轮询失败设置的“实时显示读取中断；任务状态以持久记录为准。”永远不会展示（上方 `if(!value&&!error)return null` 表明本意是要展示该提示）。同时该提前返回后 350ms 轮询定时器仍在运行直到任务终态，界面无任何可见反馈。建议在提前返回时仍渲染错误提示/状态占位，并在取到终态 value 后停止轮询。
- 判断：LiveDisplay对PERSISTED/IDLE/UNAVAILABLE直接return null，后续请求失败error被隐藏。隐去临时正文时应保留读取失败反馈，不能仅因显示状态停止后续轮轮询。
- 证据：frontend/src/Conversation.tsx:65

## OCR-2271 · 不采纳

- 位置：frontend/src/Conversation.tsx:47；OCR原严重度：low
- 计划：无
- 原意见：`changes` 已在第 39 行通过 `typeof c.beforeVersion==='string'&&typeof c.afterVersion==='string'` 过滤，因此这里的 `typeof c.beforeVersion==='string'&&` 恒为真、`String(c.afterVersion??'未知')` 的回退分支是死代码，容易让人误以为版本号可能缺失（实际缺失的条目已被过滤掉，不会显示）。建议去掉恒真判断与不可达回退，保持逻辑与实际约束一致。
- 判断：嵌套三元、!=null和防御性冗余分支不自动构成运行缺陷；OCR风格规则不能替代仓库契约。上游原样移植代码还须保留来源，不能为风格批改。
- 证据：frontend/src/Conversation.tsx:47

## OCR-2272 · 不采纳

- 位置：frontend/src/SessionActions.tsx:28；OCR原严重度：medium
- 计划：无
- 原意见：这里对未挂载到文档的游离 `<a>` 调用 `click()`：Firefox 及部分 WebView 会忽略游离节点的点击，导致下载不发生，但随后仍然 `setNotice('已导出完整事件快照…')`，界面状态与真实结果不一致（用户以为已导出，实际未落盘）。应把锚点临时挂到 `document.body` 后点击并移除，且仅在派生成功后才给出提示。
- 判断：当前Chrome/WebKit已有真实导出/交接下载成功用例，游离a.click不是这些现代目标必然失败；回收1秒尚无当前丢下载反例。可统一下载helper但不据旧浏览器通则认现有缺陷，更不能建议永不revoke造成泄漏。
- 证据：frontend/src/SessionActions.tsx:28

## OCR-2273 · 部分采纳

- 位置：frontend/src/SessionActions.tsx:27；OCR原严重度：medium
- 计划：R4-03 / P3
- 原意见：该校验失败路径的错误码 `INVALID_RESPONSE` 不在上方 `labels` 表中，会落到“操作未确认，请重读核对；不会自动重发。”——但这里并未发出任何写请求，只是导出快照的摘要不匹配，提示语义错误（会让用户以为需要重发/重读）。另外本行依赖 `crypto.subtle`，它只在安全上下文（HTTPS/localhost）可用；通过局域网 HTTP 访问时 `crypto.subtle` 为 undefined，抛出的 TypeError 会被 `e instanceof ApiError` 判为 false，显示“操作未完成，请检查连接”，把环境/完整性问题误报成连接故障。建议补 `INVALID_RESPONSE`/`EXPORT_VERIFY_FAILED` 文案并显式检查 `crypto.subtle` 可用性。
- 判断：导出完整性失败INVALID_RESPONSE未映射，显示写操作核对语义。采纳精准完整性错误提示；局域网明文HTTP不属loopback托管支持范围。
- 证据：frontend/src/SessionActions.tsx:27

## OCR-2274 · 采纳

- 位置：frontend/src/SessionActions.tsx:0；OCR原严重度：medium
- 计划：R4-01 / P2
- 原意见：“重读会话”只按 `busy` 禁用，没有看 `dirty`：当用户已输入新名称（`dirty === true`）时点击它，会用服务端值 `setName(value.name)` 无提示地覆盖用户未保存的输入，且 `onUpdated` 还会把父级 task 一并替换，属于静默丢失草稿。建议与其它按钮一致加上 `dirty` 禁用，或在覆盖前显式确认。
- 判断：SessionActions重读仅busy禁用，dirty新名称会被setName服务端值无提示覆盖。应明确丢弃确认或未保存时禁重读。
- 证据：frontend/src/SessionActions.tsx:23

## OCR-2275 · 部分采纳

- 位置：frontend/src/SessionActions.tsx:10；OCR原严重度：low
- 计划：R4-01 / P2
- 原意见：副作用体读取了 `task.name`，但依赖数组只有 `task.taskId`。父组件以 `key={active.taskId}` 重建本组件时看不出问题，但同一会话的名称在外部（其它标签页/CLI 改名后经 `detail` 刷新、或父级 `updateTask` 带回新名称）变化时，输入框仍显示旧值，`dirty` 会一直为 true，之后保存会用旧 `revision` 触发 `SESSION_CONFLICT`，或在服务端版本较新时造成覆盖。建议把 `task.name` 纳入依赖（或在 taskId 变化时同步一次并用 ref 记录已同步值），并对该非常规依赖意图加一行说明。
- 判断：同taskId父级名称更新不会同步输入，dirty可误判；只在无本地编辑时同步，不可直接加依赖导致本地草稿被覆盖。
- 证据：frontend/src/SessionActions.tsx:10

## OCR-2276 · 部分采纳

- 位置：frontend/src/SessionActions.tsx:0；OCR原严重度：low
- 计划：R4-03 / P3
- 原意见：事件 ID 的合法性上限在 `api.ts` 的 `identifier()` 中是 128 字符，这里却限制为 `maxLength={64}`，合法的 65~128 字符 eventId 无法手动输入/粘贴（只能靠默认的 `lastEvent` 兜底），属于前后端校验不一致。另外 `event` 从未按 `^[A-Za-z0-9_-]+$` 做本地校验，非法输入只有在分叉时由服务端拒绝，会落到通用的“操作未确认”提示。建议 `maxLength` 与服务端上限对齐并在提交前做一次标识符格式校验。
- 判断：原生eventId为UUID36不触64限制，但导入历史API允许较长identifier，UI字段64应与支持的导入事件范围一致并本地校验。采纳明确eventId契约/上限，避免盲目按通用identifier放宽不受支持事件。
- 证据：frontend/src/SessionActions.tsx:30

## OCR-2277 · 部分采纳

- 位置：frontend/src/ContentSearch.tsx:26；OCR原严重度：medium
- 计划：R4-01 / P2
- 原意见：第 26 行的 finally 以 `!controller.signal.aborted` 作为复位条件，而第 11 行的 effect 清理函数（`[api]` 依赖）只 abort 不复位。只要 abort 不是由 `change()` 触发（例如父组件重连后传入新的 `api` 实例、或 effect 被重新执行），`lock.current` 会永远保持 true、`busy` 永远为 true，界面卡在“正在检索…”且检索/重建按钮全部禁用，且第 19/23 行的 `if(controller.signal.aborted)return` 会连结果都不落地。建议按 controller 身份复位，而不是按 aborted 标志：`if(request.current===controller){request.current=null;lock.current=false;setBusy(false)}`，这样既能丢弃过期请求的写回，又不会被 abort 路径卡死。
- 判断：ContentSearch api依赖清理仅abort，finally因aborted不解锁，同实例换api可卡死。App常规重连可能卸载，仍采纳按controller身份复位安全复用。
- 证据：frontend/src/ContentSearch.tsx:26

## OCR-2278 · 采纳

- 位置：frontend/src/ContentSearch.tsx:20；OCR原严重度：low
- 计划：R4-01 / P2
- 原意见：翻页一致性校验不完整：只比较了 snapshotId、total 与已展示结果的重复项（页内重复与单页 20 条上限由 api.ts 保证），但没有校验累计条数上界。若服务端返回 total=25 而两页各返回 20 条且 total 保持不变，result.matches 会被累加到 40 条，界面出现“找到 25 条正文匹配，已显示 40 条”的自相矛盾输出，也突破了有界数据的展示承诺。建议追加 `result.matches.length+value.matches.length>value.total` 以及 `value.more && !value.matches.length` 的拒绝条件。
- 判断：分页未验证累计matches<=total或more空页前进，损坏响应可显示40/25。补跨页总数一致与进度保护。
- 证据：frontend/src/ContentSearch.tsx:20

## OCR-2279 · 部分采纳

- 位置：frontend/src/ContentSearch.tsx:36；OCR原严重度：high
- 计划：R4-01 / P2
- 原意见：`single` 在 `currentSession` 变为空（例如父组件切换工作区调用 `selectTask('')`）时不会被复位，而勾选框因 `disabled={!currentSession}` 既保持勾选又无法取消。此时第 18 行仍按 `single?...:''` 计算，实际发送 `sessionId: ''`，即查询整个授权范围，但界面仍宣称“仅当前会话”，会造成检索范围被静默放大、结果与用户选择不符。建议引入派生值 `const scopedSession=single&&currentSession?currentSession:''`，同时用 `checked={!!scopedSession}`，并在 `currentSession` 变为空时通过 effect 把 `single` 复位为 false。
- 判断：single不随currentSession空值复位，UI仍勾选disabled而sessionId空串按范围查；App搜索通常固定会话，但应使用有效single派生条件保持范围语义。
- 证据：frontend/src/ContentSearch.tsx:36

## OCR-2280 · 部分采纳

- 位置：frontend/src/ContentSearch.tsx:25；OCR原严重度：medium
- 计划：R4-03 / P3
- 原意见：错误归类会掩盖数据完整性问题：`api.searchContent` 在校验响应结构时抛出的 `ApiError('INVALID_RESPONSE')`（api.ts 第 175-181 行）以及本文件第 20 行自己抛出的 INVALID_RESPONSE 都会落到“内容查询未完成，请重新查询。”，引导用户反复重试一个必然失败的响应；而非 ApiError 的 `TypeError`（响应体为 null/非对象等异常形态）会被描述成“连接中断”，与真实原因不符。建议为 INVALID_RESPONSE 增加单独文案（如“服务返回的检索结果不符合协议，请检查服务端版本”），并把非 ApiError 归类为“检索结果无法解析”而非连接中断。
- 判断：INVALID_RESPONSE落通用重试文案，采纳协议/版本不符提示；Api.object(null)已转ApiError，不能宣称必然TypeError。
- 证据：frontend/src/ContentSearch.tsx:25

## OCR-2281 · 不采纳

- 位置：frontend/src/DiagnosisPanel.tsx:60；OCR原严重度：low
- 计划：无
- 原意见：离线交接下载的实现存在两处健壮性问题：(1) 未挂载到文档的 `<a download>` 在部分 WebKit/旧版浏览器中不会触发下载（本仓库含 webkit 端到端测试目标），应 append 到 body 后再 click 并移除；(2) 固定 1s 后 `revokeObjectURL` 属于猜测性时长，大交接文件在慢设备上可能尚未开始读取就被回收，且组件卸载时不会清理该定时器。建议 append/remove 后再延迟回收，并在卸载时清理定时器（或直接不回收短生命周期 Blob）。
- 判断：当前Chrome/WebKit已有真实导出/交接下载成功用例，游离a.click不是这些现代目标必然失败；回收1秒尚无当前丢下载反例。可统一下载helper但不据旧浏览器通则认现有缺陷，更不能建议永不revoke造成泄漏。
- 证据：frontend/src/DiagnosisPanel.tsx:60

## OCR-2282 · 不采纳

- 位置：frontend/src/DiagnosisPanel.tsx:34；OCR原严重度：high
- 计划：无
- 原意见：`issue.category` 来自服务端事件（`categories`/`actions` 只是普通对象字面量），这里用 `categories[...] ?? '执行问题'` 直接索引会命中原型链：若 category 为 `__proto__` 则返回 `Object.prototype` 对象，React 渲染 `Tag` 子节点时抛 "Objects are not valid as a React child" 导致整个诊断面板白屏；若为 `constructor`/`toString` 则返回函数并被当成文案渲染/`&&` 判断为真，绕过兜底文案。第 46 行 `actions[text(issue.category)] && <p>下一步…` 有同样的缺陷（还可能把函数渲染成空白或抛错）。建议改用 `Object.hasOwn` 守卫或 `Map` 查表。
- 判断：TaskDiagnosis.category是封闭if返回UNKNOWN/DENIED/CANCELLED/TIMEOUT/VALIDATION/MODEL_NETWORK/FAILED，不直接信任原事件category；__proto__无法经当前生产者进入此字段。Object.hasOwn可防御但无当前可达白屏证据。
- 证据：frontend/src/DiagnosisPanel.tsx:34

## OCR-2283 · 不采纳

- 位置：frontend/src/DiagnosisPanel.tsx:37；OCR原严重度：medium
- 计划：无
- 原意见：该单元格使用了 4 层嵌套三元表达式（commandStarted → bodyEntered → operation），违反仓库“禁止嵌套三元”的规则，且分支语义（命令主体是否启动 vs 工具主体是否进入 vs 启动情况未知）难以核对，后续新增执行模式时极易出现文案错配（例如 `execute_command` 的“包装器已进入、命令启动情况未知”分支）。建议提取为具名函数 `bodyExecutionText(issue)` 用 if/return 表达，并加一行注释说明 commandStarted/bodyEntered 的优先级。
- 判断：嵌套三元、!=null和防御性冗余分支不自动构成运行缺陷；OCR风格规则不能替代仓库契约。上游原样移植代码还须保留来源，不能为风格批改。
- 证据：frontend/src/DiagnosisPanel.tsx:37

## OCR-2284 · 采纳

- 位置：frontend/src/DiagnosisPanel.tsx:26；OCR原严重度：medium
- 计划：R4-03 / P2
- 原意见：提前返回条件只覆盖 `issues` / `sdkFactsSettled` / `uncertain(task)`，没有考虑 `task.error` 与 `task.cancelRequested`。当宿主记录了错误（例如 FAILED 但无诊断 issue）或已请求取消、且 SDK 事实已结算时，组件直接返回 null，导致第 35-36 行的“宿主记录”“已请求取消”提示以及“重新读取诊断”按钮完全不展示，用户会误以为任务正常结束。建议把 `!data.task.error && !data.task.cancelRequested` 并入 null 判断（或在 needsVerification 之外单独渲染这两类提示）。
- 判断：DiagnosisPanel提前隐藏条件未考虑task.error/cancelRequested，后面提示分支可能永远不展示；SDK结算无issues也应保留宿主错误/取消事实。
- 证据：frontend/src/DiagnosisPanel.tsx:26

## OCR-2285 · 部分采纳

- 位置：frontend/src/ExtensionEditor.tsx:0；OCR原严重度：high
- 计划：R4-02 / P2
- 原意见：第 42 行（以及第 58 行脚本超时、第 85 行 maxSteps/durationSeconds）直接把 `Number(e.target.value)` 写回草稿： - 清空数字框 ⇒ `Number('') === 0`，写入 `"maxSteps": 0`（低于 `min={1}`）； - 输入中间态 `1e`、`-`、`.` ⇒ `NaN`，而 `JSON.stringify(NaN)` 会序列化为 `null`，草稿变成 `"timeoutSeconds": null`； - `min`/`max` 只是浏览器渲染属性，不钳制手动输入，`100` 会原样进入草稿（max=64）。 这些非法值随后会被保存按钮原样提交。建议空值/非有限数直接忽略（不触发 commit），并钳制到 `[1, max]`。
- 判断：Number('')变0、中间NaN序列化null，后端仍拒绝而非绕过门禁。应保留字符串编辑态并就地校验，不能静默clamp改变用户参数。
- 证据：frontend/src/ExtensionEditor.tsx:1

## OCR-2286 · 部分采纳

- 位置：frontend/src/ExtensionEditor.tsx:30；OCR原严重度：high
- 计划：R4-02 / P2
- 原意见：ID 校验存在多处缺口： 1. 第 29 行只对 `subagents` 做重复校验，`mcp`/`skills`/`skillRoots`（数组形态）可以提交重复 ID，而第 70 行帮助文本明确承诺“同类 ID 必须唯一”； 2. 所有 kind 都没有校验 ID 字符集，含空格、`/`、中文的 ID 会被直接写入草稿； 3. 即使 `subagents`，把 ID 清空后（`row.id === ''`）`String(id)` 会生成空键 `""`；若行内缺失 id（例如高级 JSON 中键为字符串但被展开覆盖）则会写出 `"undefined"` 键，条目会互相覆盖或损坏； 4. 第 89 行生成的 ID 只用 `randomUUID().slice(0, 6)`（24 位），对非 subagents 类型完全不检查碰撞，重复 ID 会在后端装配时互相覆盖。 建议统一做 `/^[A-Za-z0-9_-]+$/` + 非空 + 跨行唯一校验，冲突时重新生成。
- 判断：表单仅subagents校验重复，数组id和空id可写草稿；后端仍有校验。采纳统一非空/格式/唯一性提示及生成碰撞检查，不能据前端缺检断言后端覆盖。
- 证据：frontend/src/ExtensionEditor.tsx:30

## OCR-2287 · 部分采纳

- 位置：frontend/src/ExtensionEditor.tsx:39；OCR原严重度：medium
- 计划：R4-02 / P2
- 原意见：`lines()`/`string()` 对非法类型静默返回空值，破坏了第 12 行注释承诺的“无效输入保留以便修正”： - 第 39 行：若 `command` 是字符串而非数组（例如手写 `"command": "python x"`），textarea 显示为空，用户一旦在里面输入/清空，原值就被 `array()` 结果覆盖为数组； - 第 60 行资源白名单：`{"file":"a","other":123}` 中 `123` 显示为空路径框，用户在该框输入（或输入后再删除）就会把 `123` 永久改成 `""`； - 第 9 行 `array()` 还会 trim 并丢弃空行，多行命令中的空行/首尾空格在任意一次编辑后丢失。 建议对非期望类型禁用该输入并提示“请修正高级 JSON”，而不是用空值渲染后覆盖原数据。
- 判断：string/lines把非法类型显示空值，后续编辑替换原JSON；array trim会改变argv首尾空格。应显式提示类型异常并保留原数据，不随意规范化命令参数。
- 证据：frontend/src/ExtensionEditor.tsx:39

## OCR-2288 · 部分采纳

- 位置：frontend/src/ExtensionEditor.tsx:76；OCR原严重度：high
- 计划：R4-02 / P3
- 原意见：`tokenEnv`、`url`、`cwd` 都是完全无校验的自由文本（第 76 行），仅靠帮助文案约束： - 用户可以在这里粘贴真实 token（帮助文本只“建议”不要填），值会以明文写入设置 JSON 并被保存； - 填写 `AWS_SECRET_ACCESS_KEY`、`GITHUB_TOKEN` 之类宿主敏感变量名，会让 MCP 客户端把宿主凭据作为鉴权头发给第三方服务； - `cwd` 接受 `../..`、`C:\\Windows` 等越界路径，`url` 也不校验 scheme/loopback。 建议前端至少按 `^[A-Z][A-Z0-9_]*$` 且长度受限校验 `tokenEnv`，并明确拒绝看起来像凭据的长随机串；同时确认后端对变量白名单、工作区边界与 URL scheme 做强制校验（不能只依赖此处的提示文案）。
- 判断：EcosystemConfig已强制MCP id/env名/transport及仅HTTPS或loopback HTTP，HTTP还禁止cwd；本机显式tokenEnv允许引用用户选定变量不是越权。只采纳表单同构格式错误提示，不限制JHARNESS前缀或泛拒合法随机名字。
- 证据：frontend/src/ExtensionEditor.tsx:76

## OCR-2289 · 不采纳

- 位置：frontend/src/ExtensionEditor.tsx:90；OCR原严重度：medium
- 计划：无
- 原意见：第 90 行是三层嵌套三元（mcp → skills → skillRoots → subagents 默认值），第 55-61 行还有 `mode === 'script' ? ... : mode === 'effect' ? ... : ...` 两处嵌套三元，违反项目“禁止嵌套三元表达式”的规则，且新增 kind 时极易漏改默认值。建议改为 `newRow(kind, id)` 工厂函数 + 标签常量映射。
- 判断：嵌套三元、!=null和防御性冗余分支不自动构成运行缺陷；OCR风格规则不能替代仓库契约。上游原样移植代码还须保留来源，不能为风格批改。
- 证据：frontend/src/ExtensionEditor.tsx:90

## OCR-2290 · 采纳

- 位置：frontend/src/ExtensionEditor.tsx:14；OCR原严重度：low
- 计划：R4-02 / P3
- 原意见：`error` 仅在 `commit` 成功时清空（第 32 行），没有随 `value` 的外部变化重置。复现：在表单里把两个角色名改成重名 → 出现“该角色名称已存在”；随后在下方“高级 JSON”textarea 里改掉重名使草稿恢复合法 → 表单重新渲染，但这条错误依旧显示，用户会以为草稿仍非法。建议在 `value` 变化时清空该状态（useEffect 依赖 value，或由父组件传 key 让实例随草稿版本重建）。
- 判断：error仅commit成功清空，高级JSON外部修正value后旧冲突错误仍驻留。应按value重新验证或清除陈旧错误。
- 证据：frontend/src/ExtensionEditor.tsx:14

## OCR-2291 · 不采纳

- 位置：frontend/src/main.tsx:11；OCR原严重度：medium
- 计划：无
- 原意见：`document.getElementById('root')!` 使用非空断言绕过了必需的 null 检查。若挂载点在构建产物中被重命名/移除，或脚本被提前注入到 `<div id="root">` 之前的 DOM（例如模板被替换、SSR 水合差异、第三方脚本重写 body），`createRoot(null)` 会抛出 "Target container is not a DOM element" 这类难以定位的错误，页面白屏且无任何业务提示。建议显式判空并给出可诊断的报错信息。
- 判断：受管index.html有root且module启动，createRoot已有明确缺挂载目标报错；假设模板被外部破坏不算当前产品缺陷。
- 证据：frontend/src/main.tsx:11

## OCR-2292 · 部分采纳

- 位置：frontend/src/upstream/Button.tsx:31；OCR原严重度：high
- 计划：R4-04 / P3
- 原意见：`{...rest}` 在 `type="button"` 之后展开，因此调用方传入的 `type`（或显式 `type: undefined`）会覆盖安全默认值，使按钮回落到浏览器默认的 `submit`，在表单内点击会触发非预期提交（例如工具栏按钮误提交表单）。同时 `ButtonHTMLAttributes` 本身已包含 `type`，二者会静默冲突。建议把 `type` 从 props 中解构出来并给出默认值，而不是在 JSX 上硬编码。
- 判断：显式type=submit是当前表单Button必要能力，不能禁用透传；仅type:undefined可能覆盖button默认。采纳解构默认type并保留显式submit。
- 证据：frontend/src/upstream/Button.tsx:31

## OCR-2293 · 不采纳

- 位置：frontend/src/upstream/Button.tsx:33；OCR原严重度：medium
- 计划：无
- 原意见：`icon` 与 `children` 均为可选且组件未对可访问名称做任何约束，仅传 `icon` 时会渲染出一个无可访问名称的按钮（图标按钮/纯图标工具栏项），屏幕阅读器只会读出空按钮，属于 a11y 契约缺失。建议：把图标容器标记为 `aria-hidden="true"`（避免重复朗读图标内容），并在开发环境下当无 `children` 且无 `aria-label`/`aria-labelledby` 时给出告警以强制调用方补全名称。
- 判断：App所有真实纯图标Button都有aria-label（任务详情/导航/关闭/发送等），有文本调用天然名称。icon props可选不证明当前无名按钮；泛化dev警告属新增组件约束。
- 证据：frontend/src/upstream/Button.tsx:33

## OCR-2294 · 不采纳

- 位置：frontend/src/upstream/Button.tsx:32；OCR原严重度：low
- 计划：无
- 原意见：`icon != null` 使用了宽松不等比较，与仓库强制的严格相等规则冲突（也会掩盖其他假值/异常对象的情况）。请改为显式空值判断。
- 判断：嵌套三元、!=null和防御性冗余分支不自动构成运行缺陷；OCR风格规则不能替代仓库契约。上游原样移植代码还须保留来源，不能为风格批改。
- 证据：frontend/src/upstream/Button.tsx:32

## OCR-2295 · 部分采纳

- 位置：frontend/src/WorkspacePicker.tsx:15；OCR原严重度：high
- 计划：R4-02 / P2
- 原意见：这里假定“新注册的工作区一定在返回列表的最后一位”，并使用了非空断言 `!`。该顺序并非 `/v1/workspaces` 的协议保证（服务端只是回传 `workspaces` 数组），一旦注册登记表的持久化顺序/排序发生变化（例如未来按名称或 id 排序、默认工作区插入位置调整、或列表为空），就会选中错误的工作区；而 `at(-1)!` 在空数组时会抛 TypeError。注意此时服务端已经完成注册，前端抛错会造成“实际已注册但界面提示失败/未选中”的状态不一致。建议通过对比注册前后的 id 集合来精确定位新建条目，而不是依赖位置。
- 判断：at(-1)定位新注册项依赖服务端追加顺序，当前行为需契约化。采纳明确返回注册id/前后差集及空列表保护，而非一律认定当前排序错误。
- 证据：frontend/src/WorkspacePicker.tsx:15

## OCR-2296 · 采纳

- 位置：frontend/src/WorkspacePicker.tsx:13；OCR原严重度：medium
- 计划：R4-02 / P2
- 原意见：注册请求没有卸载/`api` 变更保护：`register()` 在 `await` 之后无条件下调 `onSelect`/`onProfiles` 并 `setRows`。若组件在请求飞行中被卸载（例如 App 因 401 把 `api` 置空、或路由/面板切换），回调仍会对已卸载的父组件做状态更新与选中重置；若期间 `api` 换成了另一实例，还会把旧连接返回的工作区列表写进新连接的状态。列表加载路径有 `cancelled` 标志，注册路径却没有，二者不一致。建议用 ref 记录存活状态（或 AbortSignal）并在 `await` 后校验，同时校验发起请求时的 `api` 是否仍是当前实例。
- 判断：register await后无api身份/卸载校验而onProfiles/onSelect会改父状态；旧连接响应可覆盖新上下文，应请求代次保护。
- 证据：frontend/src/WorkspacePicker.tsx:13

## OCR-2297 · 部分采纳

- 位置：frontend/src/WorkspacePicker.tsx:9；OCR原严重度：medium
- 计划：R4-02 / P3
- 原意见：`onProfiles` 被闭包捕获但未列入依赖数组（违反 react-hooks/exhaustive-deps）：父组件传入的是内联箭头函数（App.tsx 中每次渲染都是新引用），因此这里拿到的是首次渲染时的旧回调。当前旧回调只用到稳定的 setState 所以尚不致错，但一旦父回调开始依赖新的 props/state 就会出现读取陈旧值且难以定位的问题（直接把它加入依赖又会导致渲染循环）。建议用 ref 保存最新回调，在解析后调用 ref。另外列表加载失败后没有任何重试入口（此时 `canRegister` 为 false，注册面板也被隐藏），用户只能整页重连才能恢复。
- 判断：当前onProfiles只用稳定setState，旧闭包尚无实际错；列表失败确无本组件重试。采纳重试/最新回调ref，避免依赖内联回调循环拉取。
- 证据：frontend/src/WorkspacePicker.tsx:9

## OCR-2298 · 部分采纳

- 位置：frontend/src/WorkspacePicker.tsx:25；OCR原严重度：low
- 计划：R4-02 / P3
- 原意见：`canRegister` 只表示服务端具备注册能力，不代表还能继续注册：服务端上限是 16 个工作区（登记表满时返回 `WORKSPACE_LIMIT`），但注册成功后这里既不更新 `canRegister` 也不结合 `rows.length` 判断，导致名额已满时注册面板仍然可编辑可提交，用户只在提交后才看到“最多注册 16 个工作区”的报错。建议在 UI 侧用 `rows.length < 16` 一起控制面板/按钮的可用性（同时保留服务端校验作为兜底）。
- 判断：面板不按16项容量禁用，后端WORKSPACE_LIMIT仍正确拒绝；采纳容量提示，非权限问题。
- 证据：frontend/src/WorkspacePicker.tsx:25

## OCR-2299 · 不采纳

- 位置：frontend/src/upstream/Input.tsx:20；OCR原严重度：low
- 计划：无
- 原意见：`icon != null` 使用了非严格的 `!=` 比较，违反项目强制规则（禁止 `==`/`!=`）。同时该装饰性图标 span 未做无障碍处理：leading icon 通常只是视觉装饰，屏幕阅读器会把它与 input 一起朗读，建议加 `aria-hidden="true"`（或允许调用方传入 `aria-hidden`）。
- 判断：嵌套三元、!=null和防御性冗余分支不自动构成运行缺陷；OCR风格规则不能替代仓库契约。上游原样移植代码还须保留来源，不能为风格批改。
- 证据：frontend/src/upstream/Input.tsx:20

## OCR-2300 · 不采纳

- 位置：frontend/src/upstream/Input.tsx:19；OCR原严重度：medium
- 计划：无
- 原意见：`className` 被解构并挂到外层 `span` 上，而 `{...rest}` 中的 `style`/`id`/`onChange` 等仍落到原生 `<input>`。这导致消费方（尤其是表单库、错误态/禁用态样式、依赖 `.className` 选择真实 input 的测试）静默作用在 wrapper 上，无法给输入框本身设置类名，属于 API 语义不一致。建议把 `className` 透传给 `<input>`，另加 `wrapperClassName` 用于外层容器；或至少新增 `inputClassName` 并在此处注释说明分流规则。
- 判断：Input className作用wrapper是现有图标/边框原子API，不能为假定表单库需求改变挂点破坏调用；inputClassName是可选新增能力。
- 证据：frontend/src/upstream/Input.tsx:19

## OCR-2301 · 部分采纳

- 位置：frontend/src/upstream/Input.tsx:21；OCR原严重度：low
- 计划：R4-04 / P3
- 原意见：组件把 `disabled` 只通过 `{...rest}` 传给内部 input，外层 wrapper 与图标不会感知禁用态，禁用时图标仍是正常视觉样式，容易造成「看起来可交互」的误导。建议在解构 `disabled` 后同样将状态类应用给 wrapper（如 `css.disabled`），或提供 `aria-disabled`。
- 判断：disabled透传原生input仍阻止编辑，但wrapper/icon无禁用视觉；采纳一致禁用表现，非安全门禁缺失。
- 证据：frontend/src/upstream/Input.tsx:21

## OCR-2302 · 部分采纳

- 位置：frontend/src/Questions.tsx:62；OCR原严重度：high
- 计划：R4-01 / P1
- 原意见：第 62 行存在两个问题： 1) `crypto.randomUUID()` 在 `try` 之外执行，而 `crypto.randomUUID` 仅在安全上下文（HTTPS/localhost）可用。若页面通过 HTTP 或旧浏览器打开，这里会抛 TypeError；`answer()` 是以 `void answer(...)` 调用的，异常变成未处理的 Promise rejection，用户既看不到错误提示，也不会进入 busy 态。 2) 这里的 `id` 是服务端的幂等键（见 `api.answerQuestion` 的 requestId）。每次点击提交都生成新 UUID，意味着网络超时/响应丢失后的“重试”其实是新请求，不具备去重能力；而 sessionStorage 中已经存了原请求 id（`marker(key)`），应当复用它。 建议：先取已保存的 marker 作为幂等键，缺失时再生成，并把生成过程放进 try 内、给出可读的错误。
- 判断：waiting已有marker会阻止常规超时再提交，非每次超时必换id；显式清标记流程应保留原幂等id，并将UUID创建置异常处理内。非loopback HTTP不在支持范围。
- 证据：frontend/src/Questions.tsx:62

## OCR-2303 · 部分采纳

- 位置：frontend/src/Questions.tsx:77；OCR原严重度：high
- 计划：R4-01 / P1
- 原意见：这一整行（第 77 行）包含两个会导致 UNKNOWN 被重放的问题： 1) 「核对回答」用的是 `api.questions(q.sessionId)`，其内部为 `questionPage(id)` 默认 `offset=0`，最多只返回前 8 条。当当前卡片位于第二页（offset>=8，且 offset 上限为 64、total 上限为 64）时 `rows.find` 必然失败，界面显示「未找到问题」，把“这一页查不到”误报成“服务端没有记录”，进而诱导用户清除标记后重发。 2) 在 `q.state==='PENDING'` 时点击「清除本页标记」会立刻把 `waiting` 置为 false，而 `open` 仍为 true，提交按钮重新可用；随后的 `answer()` 会用全新的 `crypto.randomUUID()` 再 POST 一次 /answer。如果原请求其实已到达服务端（只是响应丢失/超时），就会产生重复回答，违反“UNKNOWN 不得重放”。 建议：核对时按 questionId 精确查询（或至少带上当前 offset）；清除标记前要求先确认服务端状态不是 SUBMITTED/ANSWERED；确实要重试时复用原 requestId 做幂等重放，而不是生成新 UUID。
- 判断：核对固定第一页8条确会找不到后页问题；采纳按questionId/全分页核实。清标记后后端仍按question状态保护，不能直接宣称工具执行重放；应保留原id并基于事实核对。
- 证据：frontend/src/Questions.tsx:77

## OCR-2304 · 部分采纳

- 位置：frontend/src/Questions.tsx:7；OCR原严重度：medium
- 计划：R4-01 / P1
- 原意见：`marker()` 在 sessionStorage 不可用或写入失败（隐私模式、配额超限、被策略禁用）时统一返回 `''`，把“读写失败”和“没有标记”混为一谈：第 62 行 `marker(key,id)` 写不进去时不会报错，刷新后第 32 行 `useState(() => marker(key))` 得到空串，`waiting` 变 false、提交按钮可用，用户就会对结果未知的回答再次提交。 另外该 key 只在卡片被渲染且 state 离开 PENDING/SUBMITTED 时（第 41 行）才删除；翻页、切换 session 或列表为空导致卡片不再渲染时，`jh-answer-*` 键会永久残留，sessionStorage 持续增长。 建议：写失败时显式返回失败标志并禁止重复提交/提示用户，同时在 session 切换时集中清理该前缀的键。
- 判断：marker吞storage异常把不可保存当无标记，采纳显式失败与内存pending及刷新提示；不采纳切会话无条件清未知marker，那会破坏对账。
- 证据：frontend/src/Questions.tsx:7

## OCR-2305 · 采纳

- 位置：frontend/src/Questions.tsx:44；OCR原严重度：medium
- 计划：R4-01 / P2
- 原意见：第 44 行把 `waiting` 也作为提前 return 的条件，而 effect 依赖里含 `waiting`（第 57 行）：点击提交后 `setPending(id)` 使 `waiting` 变 true，React 会先执行上一次 effect 的 cleanup（`clearTimeout(timer)`），再进入本次提前 return，于是已排队的 350ms 草稿保存被直接取消。触发条件很常见：用户停止输入后 350ms 内点提交，且提交请求失败/超时——此时服务端既没有落盘草稿，界面又进入待核对状态，刷新后用户拿不到自己填的内容去核对或重新提交。 建议：进入 waiting 时不要取消已排队的保存（把 `waiting` 从这里的提前返回条件中去掉），或在发起 `answerQuestion` 之前先 flush 一次草稿保存。
- 判断：350ms内提交导致waiting重跑effect并取消排队的草稿保存，若响应丢失就无服务端草稿恢复。应先保留待核对答案快照或flush草稿。
- 证据：frontend/src/Questions.tsx:44

## OCR-2306 · 不采纳

- 位置：frontend/src/Questions.tsx:10；OCR原严重度：high
- 计划：无
- 原意见：切换 session（`id` 变化）时没有重置 `offset`/`rows`/`total`/`hasWaiting`： 1) 新 session 首屏会沿用上一 session 的 `rows`（卡片 key 为 `questionId`），用户可能把已切换掉的会话的问题当成当前会话的问题去回答；`api.answerQuestion` 使用的是行内 `q.sessionId`，会静默作用到旧会话。 2) `offset` 保留旧值，若大于新 session 的 `total`，第 19 行 `questionPage` 返回空数组，第 26 行 `if (!rows.length && !error) return null` 直接不渲染分页控件，用户被卡在空页且无法回到 offset=0（total 收缩后同样会死锁）。 建议：在 `id` 变化时清空 rows/total/hasWaiting 并把 offset 归零（或对组件加 `key={id}`）；同时空页时仍渲染分页控件，并把页大小 8（第 10、16-27、27 行多处硬编码）提取为常量。
- 判断：App.tsx:313以questions-+active.taskId为key挂Questions，切会话会重建offset/rows/total/guards；OCR忽略调用方key，total当前只追加。
- 证据：frontend/src/Questions.tsx:10

## OCR-2307 · 采纳

- 位置：frontend/src/Questions.tsx:63；OCR原严重度：medium
- 计划：R4-01 / P3
- 原意见：成功拿到 receipt 后没有把 `dirty` 置为 false。若服务端状态停留在 SUBMITTED（第 41 行的清理只在离开 PENDING/SUBMITTED 时触发），`dirty` 会一直为 true，第 58 行的 `beforeunload` 监听持续生效：用户回答已被接收后关闭/刷新页面仍会弹出“确认离开”，且草稿状态与实际已提交的事实不一致。建议在 receipt 表示已接收/已记录时 `setDirty(false)`。
- 判断：answer成功receipt只setNotice不清dirty，SUBMITTED时未保存标记/beforeunload仍可能存在，应区分已接收答案与未存编辑。
- 证据：frontend/src/Questions.tsx:63

## OCR-2308 · 部分采纳

- 位置：frontend/src/Questions.tsx:23；OCR原严重度：low
- 计划：R4-01 / P3
- 原意见：固定 700ms 轮询且没有任何退避与可见性判断：接口失败时仍按 700ms 持续重发（`catch` 里只设错误文案），标签页隐藏时也在持续请求，一个长会话会长期占用连接/CPU。建议改为递归 `setTimeout` + 指数退避下限（例如 700ms → 1.4s → …，成功后再回落到 700ms），并在 `document.hidden` 时暂停；另外 `reading` 标志在请求永不 settle 时会让轮询永久停止，可配合 AbortController/超时兜底。
- 判断：固定700ms请求对失败和隐藏页无退避属实际开销；Api已有响应界限，不能凭组件无Abort推断永挂。采纳失败退避/可见性暂停。
- 证据：frontend/src/Questions.tsx:23

## OCR-2309 · 不采纳

- 位置：frontend/src/Questions.tsx:72；OCR原严重度：low
- 计划：无
- 原意见：<legend> 中的 `q.mode==='single'?...:q.mode==='multiple'?...:...` 与 onChange 里 `q.mode==='single'?[o.id]:old.includes(o.id)?old.filter(...):[...old,o.id]` 都是嵌套三元表达式，违反项目规则（禁止嵌套三元）。建议抽成具名函数/映射表，例如 `const modeHint = { single: '请选择一项', multiple: '可选择多项', text: '填写你的回答' }`，以及一个 `toggleOption(old, id, mode)` 纯函数。
- 判断：嵌套三元、!=null和防御性冗余分支不自动构成运行缺陷；OCR风格规则不能替代仓库契约。上游原样移植代码还须保留来源，不能为风格批改。
- 证据：frontend/src/Questions.tsx:72

## OCR-2310 · 部分采纳

- 位置：frontend/src/SkillsPicker.tsx:17；OCR原严重度：high
- 计划：R4-02 / P2
- 原意见：自动加载绕过了组件自身的守卫：这里只判断 `prefix`/`loaded`/`busy`/`error`，没有校验 `profile` 是否为空、也没有尊重 `disabled`。触发条件一：`App.tsx` 新建任务区先用 `profile=''` 渲染 SkillsPicker，用户输入 `/` 时 `load()` 会调用 `api.skills('')`，而 `api.ts` 的 `identifier('')` 会在本地抛 `INVALID_RESPONSE`，随后统一显示“技能目录读取失败。请核对已保存的根目录、frontmatter…”的误导性提示（实际并未发起任何请求）。触发条件二：当 `disabled` 为真（`uncertain(task)`/`archived`/`busy`）时，只要 `prefix` 仍存在且 `busy` 由真转假，effect 会再次发起目录读取，使只读/UNKNOWN 态仍产生网络读取。建议在 effect 内先判断 `if (!profile || disabled || prefix === undefined) return`，并在依赖中加入 `disabled` 和 `profile` 的空值判断。
- 判断：slash自动effect未守卫空profile/disabled，空profile导致本地INVALID_RESPONSE却提示技能根错误；UNKNOWN下只读查询不等同重放。采纳条件守卫/准确错误。
- 证据：frontend/src/SkillsPicker.tsx:17

## OCR-2311 · 采纳

- 位置：frontend/src/SkillsPicker.tsx:12；OCR原严重度：medium
- 计划：R4-02 / P2
- 原意见：重新扫描时没有作废旧快照：`load()` 不复位 `loaded`/`skills`，候选按钮的 `disabled` 也只依赖 `disabled` 而未考虑 `busy`。触发条件：目录已读取成功后点“重新扫描已保存目录”，在新请求返回前用户仍可从旧快照点击“使用 /x”；如果旧技能文件已被删除/改名，草稿里插入的 `/x` 与装配时不存在同名工具，模型侧加载会失败。同样地，重新扫描失败后 `error` 被设置而 `loaded` 仍为 true，旧候选继续可点，界面同时展示“读取失败”和可选的旧条目。建议 `load()` 起始处 `setLoaded(false); setSkills([])`（或按新快照原子替换前禁用候选按钮），并让候选按钮 `disabled={disabled || busy}`。
- 判断：重扫描不清旧skills/loaded且候选不按busy禁用，失败后旧条目可继续选。应原子切换快照或读取中禁用旧候选并标记陈旧。
- 证据：frontend/src/SkillsPicker.tsx:12

## OCR-2312 · 部分采纳

- 位置：frontend/src/SkillsPicker.tsx:25；OCR原严重度：medium
- 计划：R4-02 / P3
- 原意见：每个候选都把完整的 `s.text` 挂进 `<pre>`，即使 `<details>` 处于折叠状态（折叠不会阻止子节点创建）。单条技能正文上限 64KiB、候选上限 24 条（整体响应 ≤480KiB），一次展开目录会把这些文本全部建成 React 节点与 DOM 文本节点，放大内存与首帧开销。建议只在 `<details open>` 时渲染正文（如用 `onToggle` 维护展开集合，或 `<details>` 内改用惰性组件按需读取已缓存的 `s.text`），折叠态只显示摘要/SHA256。
- 判断：details折叠仍挂所有技能正文，但响应已有480KiB限制；采纳惰性DOM展开优化，非无界内存漏洞。
- 证据：frontend/src/SkillsPicker.tsx:25

## OCR-2313 · 部分采纳

- 位置：frontend/src/SkillsPicker.tsx:34；OCR原严重度：high
- 计划：R4-01 / P2
- 原意见：`alive` 这个存活标记在 `api`/`taskId` 变化时先被 cleanup 置 false、随后立刻被重新置 true，因此旧请求在依赖切换后仍会通过判定：一个旧的 `api.tools(taskId)` 响应会写到新上下文（例如重连后新的 `Api` 实例），而 `value`/`error`/`busy` 又都没有在切换时清空。更具体的并发问题：切换后用户为新上下文发起 `load()`，旧请求的 `finally { setBusy(false) }` 会把新请求的“正在读取”状态提前清掉，使按钮重新可点，产生重复请求，且两次响应按到达顺序互相覆盖（可能展示上一任务/上一连接的清单）。建议改用请求序号或 AbortController 做请求级失效（`const seq = ++request.current`，回调里校验 `seq === request.current`），并在依赖变化时清空 `value`/`error`/`busy`。
- 判断：ToolInventory复用alive在api/taskId变化又为true，旧请求可写回；TurnComposer按会话key已限跨会话风险，api重用仍需代次保护/清旧value。
- 证据：frontend/src/SkillsPicker.tsx:34

## OCR-2314 · 不采纳

- 位置：frontend/src/SkillsPicker.tsx:13；OCR原严重度：low
- 计划：无
- 原意见：三层嵌套三元表达式承载错误码到文案的映射，既违反“禁止嵌套三元”的规则，也让新增错误码时极易把分支挂错位置。建议改为 `const messages: Record<string, string> = { SKILL_NAME_COLLISION: '技能重名。请在设置中为技能保留唯一名称。', SKILL_COMMAND_CONFLICT: '技能名与命令冲突。请修改技能名称后重新读取。' }` 再 `setError(e instanceof ApiError ? messages[e.code] ?? DEFAULT : DEFAULT)`。
- 判断：嵌套三元、!=null和防御性冗余分支不自动构成运行缺陷；OCR风格规则不能替代仓库契约。上游原样移植代码还须保留来源，不能为风格批改。
- 证据：frontend/src/SkillsPicker.tsx:13

## OCR-2315 · 不采纳

- 位置：frontend/src/upstream/Pill.tsx:28；OCR原严重度：medium
- 计划：无
- 原意见：`{...rest}` 在 `type="button"` 之后展开，而 `ButtonHTMLAttributes<HTMLButtonElement>` 本身就包含 `type`。调用方只要传入 `type="submit"`（例如在 `<form>` 内复用 Pill），显式写死的 `type="button"` 会被静默覆盖，点击后触发表单提交/页面跳转——与这里固定按钮类型的意图相反，且 TypeScript 不会报错。建议把 `{...rest}` 移到 `type` 之前（或显式剔除 rest 中的 `type`），保证组件始终渲染 `type="button"`。
- 判断：Pill透传type是显式native调用能力，当前tab调用未传submit；强制固定button会破坏正常复用，未发现意外提交实例。
- 证据：frontend/src/upstream/Pill.tsx:28

## OCR-2316 · 部分采纳

- 位置：frontend/src/upstream/Pill.tsx:24；OCR原严重度：medium
- 计划：R4-04 / P3
- 原意见：非交互分支只消费了 `className` 与 `children`，`rest` 里的 `id`、`title`、`role`、`aria-*`、`data-*`、`tabIndex` 全部被丢弃。触发条件：任何不传 `onClick` 的调用方（如文档中提到的只读状态胶囊）传入可访问名、`role` 或测试选择器时，这些属性静默失效；由于 props 类型仍是按钮属性，TS 也不会提示。建议把 span 可接受的属性转发到 span 上，或把静态分支的 props 类型收窄为 `HTMLAttributes<HTMLSpanElement>` 以便编译期发现不兼容属性。
- 判断：静态Pill span丢rest但props仍接受button属性，title/aria可静默失效。采纳静态/交互类型区分及安全属性透传，并按上游来源约束维护。
- 证据：frontend/src/upstream/Pill.tsx:24

## OCR-2317 · 不采纳

- 位置：frontend/src/upstream/Pill.tsx:23；OCR原严重度：low
- 计划：无
- 原意见：元素类型由 `!onClick` 的真值判断决定：调用方条件性省略 `onClick` 时，同一位置的节点会在 `<span>` 与 `<button>` 之间切换，React 会卸载并重建 DOM，导致焦点丢失、CSS 过渡重置、ref/节点标识失效。同时该组件未用 `forwardRef` 转发 ref（SegmentedTabs.tsx:48 只能用 `querySelectorAll('[role="tab"]')` 兜底取节点），外部无法稳定持有按钮元素。建议把交互/静态拆成显式变体（`as` 参数或两个组件），并至少把判断改为 `onClick === undefined`、转发 ref。
- 判断：是否onClick决定span/button是明确交互契约，调用条件改变的DOM重建不自身构成缺陷；无当前焦点/ref失败实例，不扩展as/forwardRef。
- 证据：frontend/src/upstream/Pill.tsx:23

## OCR-2318 · 不采纳

- 位置：frontend/src/upstream/Pill.tsx:16；OCR原严重度：low
- 计划：无
- 原意见：`active` 只切换 CSS 类，不向 DOM 输出任何 ARIA 状态（`aria-pressed` / `aria-current` / `aria-selected`），而文档把它描述为“selected/active visual state”。使用 `active` 表示选中态却未额外传 `aria-*` 的调用方，屏幕阅读器完全感知不到选中状态。建议在非 `role="tab"` 场景自动补 `aria-pressed={active}`（注意 `role="tab"` 下必须用调用方传入的 `aria-selected`，不能重复设置），或在注释中明确要求调用方自行提供 ARIA 状态。
- 判断：active是视觉标志，当前SegmentedTabs已显式role=tab与aria-selected；不能把所有active自动映射为aria-pressed，未发现调用遗漏。
- 证据：frontend/src/upstream/Pill.tsx:16

## OCR-2319 · 不采纳

- 位置：frontend/src/upstream/Tag.tsx:39；OCR原严重度：medium
- 计划：无
- 原意见：`children` 可选会让调用方在标签文本缺失时静默渲染出一个零宽空胶囊（例如 Questions.tsx:69 的 `labels[q.state]` 在状态未命中映射时为 undefined，会得到空标签；App.tsx:343 的 `event.payload.status` 若为空串同理）。空胶囊在视觉上不可见、屏幕阅读器也读不到任何语义，属于「看起来有状态、实际无内容」的静默降级。建议把 `children` 改为必填，调用方需自行兜底文案（如 `?? status`），从而在编译期暴露缺失标签。
- 判断：Tag为受控只读原子，当前tone与CSS匹配且有状态文本；未来新增tone守卫、任意属性扩展、禁止ReactNode均非当前可达问题。缺少当前空标签/交互嵌套反例。
- 证据：frontend/src/upstream/Tag.tsx:39

## OCR-2320 · 不采纳

- 位置：frontend/src/upstream/Tag.tsx:9；OCR原严重度：low
- 计划：无
- 原意见：`TagTone`（8 种）与 Tag.module.css 的 `[data-tone='...']` 选择器是两处独立维护的清单，当前恰好一一对应，但没有任何守卫：新增一个 tone（例如再补 `'pending'`）而忘记加 CSS 规则时，组件不会报错，只会渲染成一个不带 background/color 的裸 `.tag`，danger/warning 这类状态色会静默退化为默认灰字，问题难以在被引用处发现。建议补一个单测/快照断言每个 `TagTone` 成员在 CSS 模块中都存在对应规则，或由同一常量数组派生类型与选择器。
- 判断：Tag为受控只读原子，当前tone与CSS匹配且有状态文本；未来新增tone守卫、任意属性扩展、禁止ReactNode均非当前可达问题。缺少当前空标签/交互嵌套反例。
- 证据：frontend/src/upstream/Tag.tsx:9

## OCR-2321 · 不采纳

- 位置：frontend/src/upstream/Tag.tsx:41；OCR原严重度：low
- 计划：无
- 原意见：组件只接受 `tone/className/children`，不透传任何原生属性（`title`、`aria-*`、`data-*`、`id`），这与同目录 Pill（`...rest` + `ButtonHTMLAttributes`）的约定不一致。后果有两点：1) success/warning/danger 的状态语义目前只能靠 tinted 颜色和 `data-tone` 表达，调用方无法在不改组件的情况下补充 `aria-label`/`title` 等无障碍说明；2) “read-only 徽标”这一不变量没有在 API 层约束，`children: ReactNode` 允许传入 `<button>`/`<a>`，一旦有人这样用就会在 Settings.tsx:99、App.tsx:343 这类自身已是 `<button>` 的祖先中产生交互元素嵌套的非法结构。建议显式接受并透传 `HTMLAttributes<HTMLSpanElement>`（排除 onClick 或保留也行，但至少放开 aria/data 属性），同时在文档/类型上约束 children 只能是文本标签。
- 判断：Tag为受控只读原子，当前tone与CSS匹配且有状态文本；未来新增tone守卫、任意属性扩展、禁止ReactNode均非当前可达问题。缺少当前空标签/交互嵌套反例。
- 证据：frontend/src/upstream/Tag.tsx:41

## OCR-2322 · 采纳

- 位置：frontend/src/App.tsx:139；OCR原严重度：medium
- 计划：R4-01 / P1
- 原意见：任务列表轮询每 2 秒用整页快照无条件覆盖 tasks 与 detailTask，与 updateTask（cancel / decide / SessionActions / TurnComposer 的即时写回）之间没有请求序号或版本比较。触发条件：用户在 T0 点击取消，page 请求在 T0 之前已发出、在 updateTask 之后才返回，此时会把已经进入 CANCELLED/FINISHED 的任务回退成 RUNNING 写进 tasks 与 detailTask，顶部 Status、审批轮询的终态判断 isTerminal(active) 和诊断面板都会随之一致地回退，直到下一轮轮询才恢复。建议记录每个 taskId 最近一次本地写入的时间戳/序号（例如 localWriteAt ref），只接受不早于该时刻的快照；或对快照结果按 taskId 合并而不是整表替换。
- 判断：taskPage整页setTasks/setDetailTask与approvals独立轮询直接setState，无动作响应代次保护，早发晚返可覆盖新动作。采纳按会话请求序号/本地写回屏障，服务端STALE仍兜底。
- 证据：frontend/src/App.tsx:139

## OCR-2323 · 采纳

- 位置：frontend/src/App.tsx:257；OCR原严重度：high
- 计划：R4-01 / P1
- 原意见：审批列表存在两个并发写入方且没有序号保护：900ms 的独立轮询（api.approvals）与 decide 结束后的刷新都直接 setApprovals。触发条件：轮询请求在用户点击批准/拒绝之前发出，在 finally 中的刷新之后返回，会把已处理的审批重新写回 approvals（或覆盖较新的列表），界面再次出现可点击的“批准本次操作”，用户对同一 callId 重复发起决定（服务端只能以 STALE_APPROVAL 拒绝）。建议引入 approvalsGeneration ref：轮询与决定结果只在 generation 与当前选中会话都匹配时写入；成功决定后先在本地按 approvalId 移除该项。另外此处用 storage('jh-selected') 判断当前会话不可靠，它由 useEffect 在渲染后写入，切换会话的瞬间可能滞后，应改用 selectedRef.current。
- 判断：taskPage整页setTasks/setDetailTask与approvals独立轮询直接setState，无动作响应代次保护，早发晚返可覆盖新动作。采纳按会话请求序号/本地写回屏障，服务端STALE仍兜底。
- 证据：frontend/src/App.tsx:257

## OCR-2324 · 部分采纳

- 位置：frontend/src/App.tsx:0；OCR原严重度：medium
- 计划：R4-01 / P2
- 原意见：onOpen 是 async 回调但没有 try/catch：api.detail(id) 失败（CONNECTION_LOST、401、INVALID_RESPONSE 等）时产生未处理的 Promise rejection，界面既没有错误提示也不会关闭检索面板，用户无从得知打开失败。另外这里直接调用 showTask，绕过了 navigationGuard（导航按钮、WorkspacePicker、selectTask 都被该 guard 保护），在 TurnComposer 提交中或等待问题回答时仍可切换当前会话。建议捕获异常并 setError(errorText(e))，并在 guard 生效时忽略打开操作。
- 判断：ContentSearch已onOpen(...).catch设置错误，因此未处理拒绝主张不成立；App回调未看navigationGuard，采纳守卫一致性并核对搜索页打开时原草稿生命周期。
- 证据：frontend/src/App.tsx:303

## OCR-2325 · 部分采纳

- 位置：frontend/src/App.tsx:231；OCR原严重度：medium
- 计划：R4-01 / P1
- 原意见：成功提交后在确认连接仍是同一个 api 之前就清除了 jh-pending 标记：若提交期间发生重连（currentApi.current !== api），服务端已受理的任务既不会通过 showTask 展示，也不再留有可供 reconcile() 核对的 requestId，用户很可能重复提交同一目标。建议先判断 currentApi.current === api，确认仍为当前连接后再清除 pending 并展示任务；否则保留 pending 记录以便核对。
- 判断：submit/fork/reconcile先清storage pending再检查currentApi，旧连接响应可能丢本页受理对账标记。采纳连接身份绑定；服务端事实仍可从列表核实，不是执行事实丢失。
- 证据：frontend/src/App.tsx:231

## OCR-2326 · 不采纳

- 位置：frontend/src/App.tsx:2；OCR原严重度：low
- 计划：无
- 原意见：同一个模块 ./ContentPanel 被 import 了两次（此行与第 4 行 import { ContentPanel, type FileSelection } from './ContentPanel'），属于重复声明，建议合并为一次导入，避免后续维护时两处不同步。
- 判断：两条import导入同模块不同导出合法且无运行差异，合并只是风格。
- 证据：frontend/src/App.tsx:2

## OCR-2327 · 部分采纳

- 位置：frontend/src/upstream/SegmentedTabs.tsx:31；OCR原严重度：medium
- 计划：R4-04 / P3
- 原意见：`value` 只被文档约束为“必须属于 items”，运行时并未校验。一旦失配（例如父组件从持久化状态恢复出已废弃的 tab 值、或 items 动态裁剪后 value 未同步重置），`findIndex` 返回 -1，会产生两个可复现后果：(1) 第 74 行 `translateX(${-1 * 100}%)` 把指示条移到容器左侧之外，视觉上没有任何选中态；(2) 第 72 行所有 tab 的 `tabIndex` 都为 -1，整个 tablist 失去键盘进入点（Tab 键无法聚焦任何 tab），键盘用户完全无法切换分类。建议把失配值退化为第一个 tab（或至少保证 index 被 clamp 到 [0, items.length-1]），并让 `selectedIndex` 同时驱动 `tabIndex`/`aria-selected`，而不是继续用原始 `value` 比较。
- 判断：失配value可令tabIndex全-1，后代querySelector也依赖标签无嵌套tab；当前App静态联合类型/纯文本保证正常。采纳组件边界安全校验/直接子项/null守卫，非已证实当前导航故障。
- 证据：frontend/src/upstream/SegmentedTabs.tsx:31

## OCR-2328 · 部分采纳

- 位置：frontend/src/upstream/SegmentedTabs.tsx:48；OCR原严重度：medium
- 计划：R4-04 / P3
- 原意见：焦点目标用 `querySelectorAll('[role="tab"]')` 按 DOM 顺序 + 下标定位，缺少两点保证：一是未限定直接子节点，若某个 `item.label`（ReactNode，内容由调用方提供）内部再渲染 `role="tab"` 元素，集合顺序与 `items` 就不再一一对应，会把焦点移到错误元素；二是 `NodeList.item(next)` 在运行时可能返回 `null`（类型上声明为 `Element` 但在集合长度不足时确实返回 null），此时直接调用 `.focus()` 会抛 TypeError，导致按键处理中断、`onChange` 也不再执行。建议限定 `:scope > [role="tab"]` 并显式判空。
- 判断：失配value可令tabIndex全-1，后代querySelector也依赖标签无嵌套tab；当前App静态联合类型/纯文本保证正常。采纳组件边界安全校验/直接子项/null守卫，非已证实当前导航故障。
- 证据：frontend/src/upstream/SegmentedTabs.tsx:48

## OCR-2329 · 不采纳

- 位置：frontend/src/upstream/SegmentedTabs.tsx:42；OCR原严重度：low
- 计划：无
- 原意见：已处理的导航键调用 `stopPropagation()`，会阻止事件继续冒泡到祖先节点上的快捷键/组合控件处理逻辑（例如页面级 Left/Right 翻页、外层列表的方向键导航）。当 SegmentedTabs 被嵌入到同样监听方向键的复合组件或带全局快捷键的页面时，用户的按键会被静默吞掉。`preventDefault()` 已足以阻止按键的默认行为（滚动/焦点移动），建议移除 `stopPropagation()`，除非确实需要阻断祖先监听并在注释中说明。
- 判断：tab复合控件消费方向键并stopPropagation避免父层同时导航是合理键盘隔离，无父快捷键真实被破坏证据。
- 证据：frontend/src/upstream/SegmentedTabs.tsx:42

## OCR-2330 · 不采纳

- 位置：frontend/src/upstream/SegmentedTabs.tsx:62；OCR原严重度：low
- 计划：无
- 原意见：指示条宽度用模板字符串硬编码 `8px`（对应 CSS `.tabs { padding: 4px }` 的左右内边距）和 `inset: 4px` 的偏移，与 `SegmentedTabs.module.css` 中的 padding 形成隐式耦合：一旦模块样式调整内边距/增加 gap，指示条就会与实际列宽错位，而这种错位不会出现在类型检查或单元测试里。建议把该常量抽成组件内的具名常量并与 CSS 变量（如 `--segmented-tabs-padding`）保持单一来源。
- 判断：4px/8px是当前组件与CSS匹配的共同几何约定，未来修改须同步不代表现存错位，抽变量属维护偏好。
- 证据：frontend/src/upstream/SegmentedTabs.tsx:62

## OCR-2331 · 部分采纳

- 位置：frontend/src/TurnComposer.tsx:50；OCR原严重度：high
- 计划：R4-01 / P1
- 原意见：所有 4xx 都会清除幂等标记与会话内的 pending，包括语义上无法证明“请求未被受理”的状态码：408 请求超时、409 冲突、425、429，以及反向代理/网关在请求已转发到后端后自行合成的 4xx。此时清除 `jh-turn-*` 标记后，用户再次点击发送会生成新的 `crypto.randomUUID()` 并重新提交，可能造成同一条指令被执行两次，违反 UNKNOWN 结果不得重放的约束。建议只对明确表示“未受理/确定拒绝”的状态码（如 400/401/403/404/413/422）清除标记，其余 4xx 一律保留 pending 并提示用户核对受理状态。
- 判断：对所有4xx清pending过宽，应按本机服务明确未受理错误码白名单处理；反向代理不是当前拓扑。采纳确定拒绝/丢响应对账边界回归。
- 证据：frontend/src/TurnComposer.tsx:50

## OCR-2332 · 不采纳

- 位置：frontend/src/TurnComposer.tsx:63；OCR原严重度：high
- 计划：无
- 原意见：核对受理成功后会无条件清除 sessionStorage 标记与 pending/草稿，但 `turnStatus` 返回的 `submission.state` 可能是 `UNKNOWN`（API 层已将该值列入合法枚举）。这会在结果仍未核实的情况下丢掉唯一的重放护栏，用户可以立刻再提交一条新消息，与“UNKNOWN 不得重放”的恢复规则冲突；`states[value.submission.state]` 也只会显示“结果待核实”却已删除标记。请先判断状态：仅当状态为终态（QUEUED/RUNNING/FINISHED/CANCELLED/VERIFIED）时才清除标记，UNKNOWN 时保留 pending 与草稿并给出离线核实提示。
- 判断：查到UNKNOWN确认的是submission已存在，清本地受理marker不清服务端UNKNOWN；onUpdated(task)后uncertain继续禁发送，后端也禁未决续跑。OCR混淆受理未知与执行未知。
- 证据：frontend/src/TurnComposer.tsx:63

## OCR-2333 · 采纳

- 位置：frontend/src/TurnComposer.tsx:28；OCR原严重度：medium
- 计划：R4-01 / P1
- 原意见：轮询链路没有与用户动作的响应做时序控制：`read()` 先请求 turns 再请求 detail，而 submit/start 会直接 `callbacks.current.onUpdated(value.task)`。当动作响应返回后，早于它发出的轮询 detail 才落地，就会用旧快照覆盖父组件里更新的 task（phase/status/queue 状态回退），表现为“刚发送成功的消息又变成未发送/排队前状态”。同理 `setRows(page.submissions)` 也可能回退刚取消的队列项。建议用一个递增序号（或 `latestWrite` ref）只接受比已应用快照更新的响应；更简单的做法是在 `lock.current`/`busy` 为真时跳过本轮轮询写入。
- 判断：TurnComposer poll与submit/start/cancel响应无代次，旧detail可onUpdated覆盖新状态；采纳请求序号与动作后重新取样。
- 证据：frontend/src/TurnComposer.tsx:28

## OCR-2334 · 部分采纳

- 位置：frontend/src/TurnComposer.tsx:0；OCR原严重度：medium
- 计划：R4-01 / P2
- 原意见：队首判定只检查当前分页：`index !== 0 && rows.slice(0,index).some(...)` 认为本页第 0 条就是全局队首。当 `offset > 0`（用户翻到旧页）时，即使更早的分页里仍有 QUEUED/PAUSED，当前页第一行 PAUSED 的“开始这条消息”按钮仍会被启用，服务端将返回 QUEUE_ORDER_CONFLICT。建议在 `offset > 0` 时一律禁用开始按钮（改为翻回第一页再开始），或由接口返回队首 submissionId/sequence 后再按全局序判定。
- 判断：队首判断只本页，offset>0可误启用start但后端QUEUE_ORDER_CONFLICT拒绝；采纳全局队首或翻页保守禁用，不是队列绕过。
- 证据：frontend/src/TurnComposer.tsx:66

## OCR-2335 · 不采纳

- 位置：frontend/src/TurnComposer.tsx:23；OCR原严重度：medium
- 计划：无
- 原意见：`read()` 没有任何超时或 AbortController：一旦某次 `api.turns`/`api.detail` 的 fetch 悬挂不 resolve，`reading` 永远保持 true，1.2s 定时器会一直空转、轮询静默停止，且页面不会再显示任何错误；effect cleanup 也只是把 `stopped` 置位而不中止在途请求（旧请求仍占用连接并在之后写入已卸载组件之外的闭包状态）。建议为 each poll 传入 AbortSignal 并设置超时，在 finally 之外也保证 `reading` 可恢复（例如超时后主动 abort），并在 cleanup 中 abort。
- 判断：Api.call对响应头有12秒Abort，readJson对body另有12秒取消，TurnComposer read不会因正常fetch永久reading=true；cleanup stopped阻止写回。增加signal可减少卸载后短期连接但原永久挂起/越卸载写回主张不成立。
- 证据：frontend/src/TurnComposer.tsx:23

## OCR-2336 · 部分采纳

- 位置：frontend/src/TurnComposer.tsx:29；OCR原严重度：low
- 计划：R4-01 / P3
- 原意见：这里把 `api.turns` 与 `api.detail` 放在同一个 try 里，detail 失败时也会显示“消息队列读取失败”，与实际故障不符；同时轮询成功路径从不 `setError('')`，一次瞬时失败（或 401）后错误条会永久驻留，即使用户已重新连接、队列已恢复正常。另外 401 后仍会每 1.2s 反复请求，持续产生失败日志。建议分别捕获两个请求的错误、成功时清除上一次的轮询错误，并在 401 时暂停轮询等待重新连接。
- 判断：read成功未清旧error会残留瞬时错误；采纳独立poll错误状态及401暂停，detail失败统称队列属文案改善。
- 证据：frontend/src/TurnComposer.tsx:29

## OCR-2337 · 不采纳

- 位置：frontend/src/TurnComposer.tsx:57；OCR原严重度：low
- 计划：无
- 原意见：输入长度 16384 与错误文案中的“最多8条”“会话256条后续消息配额”都是硬编码的业务上限，服务端调整后前端会静默截断或给出错误提示。建议从接口/配置常量（共享同一份限制定义）派生这些数值，而不是在组件里复制。
- 判断：16384/8/256是当前API明确配额，前后端复制且一致不是bug；共享常量属于维护建议，未来调整再同步。
- 证据：frontend/src/TurnComposer.tsx:57

## OCR-2338 · 部分采纳

- 位置：frontend/src/TurnComposer.tsx:0；OCR原严重度：low
- 计划：R4-01 / P2
- 原意见：取消排队丢弃了 `turnAction` 返回的受理回执：既不用 `value.task` 刷新父状态，也不检查返回的 `submission.state` 就断言“已取消排队”。若服务端返回的最终状态并非 CANCELLED（例如消息在请求途中已被消费），UI 会给出错误的成功提示；在轮询停摆（见上一条）时队列项还会一直显示为排队中。建议校验回执状态后再提示，并用 `value.task` 更新状态。
- 判断：取消排队丢回执，界面靠下次poll更新；已开始后端会报错而非成功。采纳校验回执state与即时task写回。
- 证据：frontend/src/TurnComposer.tsx:66

## OCR-2339 · 部分采纳

- 位置：frontend/src/ContentPanel.tsx:64；OCR原严重度：high
- 计划：R4-03 / P2
- 原意见：`target`/`line` 的初始化不区分 `mode`：`useState(selection?.path ?? '')` 无条件用文件路径初始化，而本 effect 只在 `mode==='files'` 时同步 selection。触发条件：选择过某个文件（target=`src/Main.java`）后把面板切到「任务产物」（或带 selection 直接以 artifacts 模式挂载），target 仍为文件路径，`ContentReader` 便以 `kind='artifacts'` + 文件路径请求，`api.content` 的客户端校验（artifacts target 必须匹配 `[0-9a-f-]{36}-[a-f0-9]{64}`）直接抛 `INVALID_RESPONSE`，用户看到「读取未完成，请检查连接后重新读取。」这类误导提示，且产物根本看不了。另外 reader 的 key 含 `selection?.serial`，selection 变化但路径/行号未变时也会强制重挂载并重复请求，丢弃已翻页状态。建议 artifacts 模式清空 target/line，只把 serial 放进 key 当路径/行号确实变化时使用。
- 判断：ContentPanel同实例切mode保留target，文件路径可能被当artifact id。App虽给artifacts selection=null，state仍在；采纳mode重置，selection.serial为刻意重复定位不删。
- 证据：frontend/src/ContentPanel.tsx:64

## OCR-2340 · 部分采纳

- 位置：frontend/src/ContentPanel.tsx:48；OCR原严重度：medium
- 计划：R4-03 / P3
- 原意见：下载失败或被 Stop 中止后，`notice` 仍停留在第 32 行设置的「正在读取并校验下载内容…」（`role="status"`），catch 只设置 error、finally 只处理 downloading/busy，没有任何分支清理 notice。结果是错误提示与「正在读取」状态长期并存，用户会以为下载仍在进行。建议在 catch 中清除/改写 notice，并在 finally 统一收尾。
- 判断：download catch只setError不清notice，异常留正在校验；Stop已setNotice已停止，原意见Stop部分不成立。采纳异常收尾。
- 证据：frontend/src/ContentPanel.tsx:48

## OCR-2341 · 采纳

- 位置：frontend/src/ContentPanel.tsx:15；OCR原严重度：medium
- 计划：R4-03 / P3
- 原意见：`failure()` 没有覆盖本文件自身会抛出的 `RESPONSE_LIMIT`（第 39/41 行）以及服务端会返回的 `FILE_NOT_FOUND`、`ARTIFACT_DENIED`、`INVALID_RANGE` 等码，它们全部落到「读取未完成，请检查连接后重新读取。」——把确定性错误（路径不存在、超出 8 MiB 下载上限）说成网络问题，会诱导用户反复重试。建议补齐映射，例如 `RESPONSE_LIMIT` →「内容超过预览/下载上限（8 MiB）」、`FILE_NOT_FOUND` →「文件不存在」。
- 判断：failure缺RESPONSE_LIMIT/FILE_NOT_FOUND/ARTIFACT_DENIED/INVALID_RANGE，确定错误描述为检查连接，需精确映射。
- 证据：frontend/src/ContentPanel.tsx:15

## OCR-2342 · 采纳

- 位置：frontend/src/ContentPanel.tsx:66；OCR原严重度：low
- 计划：R4-03 / P3
- 原意见：开始新的列表请求时只重置了 entries/error，没有重置 `limited`。触发条件：进入过一个被截断的目录（limited=true）后切换目录或点「刷新」，在请求进行中或请求失败时旧的「列表已达上限，请进入子目录…」提示仍会显示，与当前列表不符，可能误导用户放弃浏览。建议在请求开始时 `setLimited(false)`。
- 判断：列表开始不清limited，重新读内容不清notice/page，旧截断/已校验下载提示可留在新请求中；采纳按请求重置陈旧状态。
- 证据：frontend/src/ContentPanel.tsx:66

## OCR-2343 · 采纳

- 位置：frontend/src/ContentPanel.tsx:23；OCR原严重度：low
- 计划：R4-03 / P3
- 原意见：重新读取/翻页时没有清理旧的 `notice`：上一次下载成功后的「已校验脱敏内容SHA-256并下载」会在新的读取过程中持续显示，与当前内容版本不符；重新读取（version 置空）期间还会继续展示旧 page 的内容与「校验与来源」摘要，用户可能把旧内容误当成重新读取后的当前内容。建议请求开始时 `setNotice('')`，并在 version 被重置的重新读取场景先清空 page。
- 判断：列表开始不清limited，重新读内容不清notice/page，旧截断/已校验下载提示可留在新请求中；采纳按请求重置陈旧状态。
- 证据：frontend/src/ContentPanel.tsx:23

## OCR-2344 · 部分采纳

- 位置：frontend/src/ContentPanel.tsx:35；OCR原严重度：low
- 计划：R4-03 / P3
- 原意见：`513` 与 `8388608` 是与服务端协议强耦合的魔数（服务端 `ContentPages` 页大小 16384 字符、内容上限 8 MiB 字符）。散落在循环条件与长度判断里，一旦服务端调整页大小或上限，这里会静默多发请求或提前误报 `RESPONSE_LIMIT`。建议提取为具名常量，并由 `MAX_CHARS/PAGE_SIZE` 推导页数上界，同时校验 `p.nextOffset > offset` 以防服务端异常时不推进。
- 判断：循环已有513页与8MiB限制，非无界；采纳具名常量及nextOffset前进检查，不能仅因魔数说协议错。
- 证据：frontend/src/ContentPanel.tsx:35

## OCR-2345 · 不采纳

- 位置：frontend/src/ContentPanel.tsx:56；OCR原严重度：low
- 计划：无
- 原意见：语言判定用了三段嵌套三元，并且 `target.split('.').at(-1)` 重复计算，可读性/可维护性差（项目约定禁止嵌套三元）。建议抽成 `function languageOf(kind, target): string` 的小函数，返回单一字符串。
- 判断：嵌套三元、!=null和防御性冗余分支不自动构成运行缺陷；OCR风格规则不能替代仓库契约。上游原样移植代码还须保留来源，不能为风格批改。
- 证据：frontend/src/ContentPanel.tsx:56

## OCR-2346 · 不采纳

- 位置：frontend/src/upstream/TextShimmer.tsx:40；OCR原严重度：medium
- 计划：无
- 原意见：The same `content` element is rendered in both the base and the decorative layer, but the `att`-based optimization only applies when `children` is a plain string. For fragments, numbers, arrays or any element tree, the whole subtree is mounted a second time as live DOM inside the decoration, so any state, effects, subscriptions, ref callbacks or `id`s in those children are duplicated (duplicate ids break `getElementById`/`label htmlFor`, refs fire twice, effects run twice). The JSDoc constraint ("No effects or element ids") is not enforced at runtime. Consider restricting the decoration to plain text (e.g. flatten/normalize `children` to a string) or emitting a dev-time warning when children are not a string.
- 判断：TextShimmer文档明确children不得effects/ids，当前调用纯文本；违反调用契约的任意ReactNode示例不证明当前重复副作用。
- 证据：frontend/src/upstream/TextShimmer.tsx:40

## OCR-2347 · 部分采纳

- 位置：frontend/src/upstream/TextShimmer.tsx:39；OCR原严重度：low
- 计划：R4-04 / P3
- 原意见：In the nested branch both `contentClassName` and the nested `active` are silently dropped. Ignoring `active` matches the documented contract, but `contentClassName` is documented as "Layout class applied equally to the base and decorative content" and is simply lost here, so nested fragments can lay out differently from the owning row. Either apply it or document explicitly that it only applies to top-level instances.
- 判断：嵌套TextShimmer忽略active符合文档，contentClassName丢失与说明不一致；采纳说明仅顶层或保留布局类，维护来源记录。
- 证据：frontend/src/upstream/TextShimmer.tsx:39

## OCR-2348 · 不采纳

- 位置：frontend/src/upstream/TextShimmer.tsx:48；OCR原严重度：low
- 计划：无
- 原意见：Spreading `{ inert: '' }` bypasses JSX type checking and depends on React 18's treatment of `inert` as a string attribute. Once the project moves to React 19 (where `inert` is handled as a boolean attribute), the empty-string value may be normalized away, leaving the `aria-hidden` decorative subtree focusable and reachable by keyboard/screen readers. Use an explicitly typed helper (or a typed wrapper attribute) so the behavior is verified by the type system and audited on React upgrades.
- 判断：当前React18 inert字符串有效，React19尚未升级；未来兼容风险应随升级验证，不列当前缺陷。
- 证据：frontend/src/upstream/TextShimmer.tsx:48

## OCR-2349 · 采纳

- 位置：frontend/src/Settings.tsx:74；OCR原严重度：high
- 计划：R4-02 / P1
- 原意见：`save()` 在 `await api.saveSettings(...)` 之前就执行 `setKeys({})`，把用户刚输入的密钥从组件状态中抹掉。一旦保存失败（`SETTINGS_CONFLICT`、45s 超时、`CREDENTIAL_STORE_UNAVAILABLE` 等），页面仍保持 `dirty`，但密钥输入框已变空且 `keys` 中没有该值；用户再点"保存设置"时会提交**不含 apiKeys 的**载荷，服务端沿用旧 `credentialRef` 或 `apiKeyEnv` 回退，界面却提示"设置已保存"，导致新密钥静默丢失且看起来已生效（与下方"留空使用已存密钥"提示叠加，极难察觉）。应在请求成功、`apply(v)` 之后再清空（或只删除已成功写入的 id）。
- 判断：save在await前setKeys({})，失败局部pendingKeys丢失但dirty保留，第二次可无新key保存却提示成功。应成功后清或失败明确重新输入并阻止误成功。
- 证据：frontend/src/Settings.tsx:74

## OCR-2350 · 部分采纳

- 位置：frontend/src/Settings.tsx:51；OCR原严重度：high
- 计划：R4-01 / P2
- 原意见：`api` 变化时存在竞态：旧 effect 的 cleanup 先把 `alive` 置 false，新 effect 立刻又置 true 并调用 `load()`；但旧请求仍在飞行，`lock.current === true`，新的 `load()` 直接 `return`，因此锁释放后新 Api 的设置**永远不会被加载**。旧请求返回时 `alive.current` 已被重置为 true，于是旧宿主的数据（含旧 `revision`）会被 `apply()` 到新的 Api 实例并清空 `busy`；此后保存必然 `SETTINGS_CONFLICT`，而在未校验 revision 的分支上还可能把旧宿主的连接列表写进新宿主。建议改为请求代次编号（generation）或按请求的 `AbortController` 判断，`api` 变化时中止在途请求，且只有最新代次的响应才允许 `apply`/清空 `busy`，不要复用全局 `lock` 来吞掉新加载。
- 判断：同实例api变化时alive又true而lock吞新load，旧响应可apply新上下文；App通常失联卸载，但组件重用仍需请求代次/取消。
- 证据：frontend/src/Settings.tsx:51

## OCR-2351 · 部分采纳

- 位置：frontend/src/Settings.tsx:102；OCR原严重度：high
- 计划：R4-02 / P2
- 原意见：凭据未与 endpoint 绑定：`update()` 在 `baseUrl`/`model`/`protocol` 变化时只清空 `pricing` 里的单价，却保留 `keys[editing]` 中已输入的密钥；后端的 `credentialRef` 也只按连接 id 保存/复用，不按 origin 绑定。因此把连接地址从 A 服务改成任意第三方 URL 后，原密钥（或本次输入的密钥）会原样作为 Bearer 发送到新地址，而占位文案还明确写着"留空保留原密钥"。另外 `type="url"` 不限制 scheme，`http://` 明文地址同样会带上密钥。建议在 origin 变化时清除 `keys[model.id]` 并要求重新输入，同时对非 loopback 的 http 地址给出明确告警或拒绝。
- 判断：明确编辑可信连接URL后保留key是当前设置能力，不是未授权秘密外发；但跨origin使用原凭据及非loopback明文HTTP易误操作，采纳保存前清晰origin/密钥复用提示与HTTP告警。不要静默清key或禁止合法代理迁移，受理快照仍绑定原endpoint。
- 证据：frontend/src/Settings.tsx:102

## OCR-2352 · 不采纳

- 位置：frontend/src/Settings.tsx:114；OCR原严重度：high
- 计划：无
- 原意见：`apiKeyEnv` 是自由输入，仅受 128 字符长度限制，没有任何前缀或白名单约束。后端只校验 `[A-Za-z_][A-Za-z0-9_]*`，并在该连接没有 `credentialRef` 时用 `environment.getOrDefault(apiKeyEnv, "")` 直接作为密钥发往用户可任意填写的 `baseUrl`。结果是把该字段填成 `AWS_SECRET_ACCESS_KEY`、`GITHUB_TOKEN` 等宿主上已存在的任意变量名，再指向一个外部地址，就能让无关环境机密被当作 API Key 外发（服务端还会把它加入脱敏集合，进一步掩盖痕迹）。建议在此限制为产品定义的环境变量前缀白名单（如 `JHARNESS_`），或在保存前对非白名单变量名强制二次确认。
- 判断：本机已鉴权设置按用户指定环境变量取key是明确功能；不能把显式本机配置视为越权读取。强制JHARNESS_会破坏DEEPSEEK_API_KEY等合法现有配置。
- 证据：frontend/src/Settings.tsx:114

## OCR-2353 · 采纳

- 位置：frontend/src/Settings.tsx:52；OCR原严重度：low
- 计划：R4-02 / P3
- 原意见：`apply()` 在每次加载和每次保存成功后都把 `editing` 重置为服务端返回的 `selected`（默认连接）。用户在编辑非默认连接 B 时点击保存，成功响应后编辑器会跳到默认连接 A，用户很容易在错误的连接上继续修改并再次保存；`save()` 与 `load()` 两条路径都受影响。建议仅当 `editing` 已不在 `v.models` 中时才回退到 `v.selected`。
- 判断：apply总把editing改默认selected，保存非默认B后编辑器跳A；应保留仍存在的editing，仅初次/删除回退默认。
- 证据：frontend/src/Settings.tsx:52

## OCR-2355 · 不采纳

- 位置：examples/broken-project/pom.xml:3；OCR原严重度：medium
- 计划：无
- 原意见：该示例作为“故意失败”的夹具，其失败语义依赖编译阶段抛出源码语法错误；但这里只声明了 `maven.compiler.release=21`，既没有 `maven-enforcer-plugin` 的 `requireJavaVersion` 约束，也没有 toolchain 配置。触发条件：在 JDK 17/11 等低于 21 的 CI 或开发机上执行 `mvnw.cmd -B -o test`（docs/testing/M4-validation.md 记录的命令），`javac` 会直接报 `release version 21 not supported`，构建在编译插件参数校验阶段就失败，而不是夹具预期的源码语法错误；harness 采集到的首个失败点因此与文档约定不一致，容易把环境问题误判为示例缺陷。建议增加 enforcer 或在示例 README/构建脚本中显式校验 JDK 版本，保证夹具失败点唯一。
- 判断：release=21在旧JDK明确报不支持目标版本，项目文档已有JDK21前置；为故意失败夹具加enforcer改变失败阶段，不能将旧JDK非支持环境算产品bug。
- 证据：examples/broken-project/pom.xml:3

## OCR-2356 · 不采纳

- 位置：examples/calculator/pom.xml:3；OCR原严重度：low
- 计划：无
- 原意见：`maven.compiler.release=21` 使该示例的编译强依赖 JDK 21+，但 POM 中没有 toolchain 声明或 maven-enforcer-plugin 的 JDK 版本校验。触发条件：CI/开发机使用 JDK 17 及以下（例如工具链默认 JVM）执行该示例构建时，失败信息来自 javac 的 `invalid target release`，而不是构建前置的明确环境校验，排查成本较高；同时 JUnit 版本在此处硬编码，与根 POM 的 `${junit.version}` 存在漂移风险（当前均为 5.12.2）。建议增加 enforcer 的 requireJavaVersion 校验或 toolchain 声明，并与根 POM 的版本属性保持一致。
- 判断：release=21在旧JDK明确报不支持目标版本，项目文档已有JDK21前置；为故意失败夹具加enforcer改变失败阶段，不能将旧JDK非支持环境算产品bug。
- 证据：examples/calculator/pom.xml:3

## OCR-2357 · 部分采纳

- 位置：examples/calculator/pom.xml:6；OCR原严重度：low
- 计划：R6-01 / P3
- 原意见：该示例工程以独立 POM 形式被构建（未继承根 POM 的 dependencyManagement/build 配置），但这里只固定了 compiler/surefire 两个插件的版本，`package`/`install` 阶段使用的 maven-jar-plugin 版本来自 Maven 发行版内置的默认生命周期绑定，且未设置 `project.build.outputTimestamp`。触发条件：用不同版本 Maven（或不同默认插件绑定的发行版）执行 `mvn package` 时，产出的 jar 可能包含不同的 manifest 条目与时间戳，导致构建产物不可复现。根 POM 已固定 jar 插件 3.4.2 并设置 outputTimestamp，建议在此 POM 同步声明。
- 判断：根和示例部分生命周期插件依赖Maven默认版本，影响长期可重复性；当前wrapper固定3.9.9。采纳固定实际使用构建插件，不为未使用deploy/site引入额外执行。
- 证据：examples/calculator/pom.xml:6

## OCR-2369 · 部分采纳

- 位置：pom.xml:28；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：No `maven-enforcer-plugin` rule ensures the build actually runs on JDK 21+ / a supported Maven version. On an older JDK the failure surfaces only as a cryptic `release version 21 not supported` error deep inside the compiler plugin, and a too-new/unsupported Maven or JDK is not caught either. Suggest adding an `enforce` execution (bound to `validate`) requiring `maven >= 3.9.0` and `java >= 21`, ideally with `project.build.outputTimestamp` reproducibility requirements.
- 判断：JDK21前置由release和CI保证，老JDK当前失败非误通过；补enforcer/清晰工具版本诊断合理，避免重复下载/副作用。
- 证据：pom.xml:28

## OCR-2370 · 部分采纳

- 位置：pom.xml:48；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：Only `jar`, `compiler`, and `surefire` are version-pinned; the remaining default-lifecycle plugins (`resources`, `install`, `deploy`, `site`, `clean`) resolve to whatever version the local Maven distribution ships. Combined with `project.build.outputTimestamp` (which implies a reproducibility goal), this makes builds non-reproducible across Maven versions and can change behavior silently (e.g. deploy/site plugin changes). Pin them too, preferably in `<pluginManagement>`.
- 判断：根和示例部分生命周期插件依赖Maven默认版本，影响长期可重复性；当前wrapper固定3.9.9。采纳固定实际使用构建插件，不为未使用deploy/site引入额外执行。
- 证据：pom.xml:48

## OCR-2371 · 不采纳

- 位置：pom.xml:53；OCR原严重度：medium
- 计划：无
- 原意见：`-Xlint:all` combined with `-Werror` is inherited by all 14 modules, so any single lint warning (deprecation, serial, try/processing in a newer JDK, annotation-processing generated sources) turns into a hard build failure. This is a known source of brittle builds when the JDK is upgraded. Suggest enabling `-Werror` only in a dedicated CI profile, or at least excluding noisy lints (e.g. `-Xlint:all,-processing,-serial`) instead of `all`.
- 判断：Werror是项目有意质量门禁，JDK21 CI固定；新JDK新增警告导致失败应显式处理，不应放宽把警告改静默通过。
- 证据：pom.xml:53

## OCR-2372 · 不采纳

- 位置：pom.xml:62；OCR原严重度：low
- 计划：无
- 原意见：Formatting is only validated at `verify`, i.e. after the full compile+test cycle. A formatting violation therefore wastes a full build and, in multi-module reactors, is reported late and per module. Binding `spotless:check` to an earlier phase (e.g. `validate` or `process-sources`) fails fast; also make sure CI and developer machines use the same plugin (2.43.0) / google-java-format (1.24.0) versions, otherwise `verify` can fail on code that formatted cleanly locally.
- 判断：verify阶段格式检查为既有门禁，晚检查影响效率非正确性；绑定validate会增加常规部分构建范围，未需为风格重排。
- 证据：pom.xml:62

## OCR-2373 · 不采纳

- 位置：pom.xml:36；OCR原严重度：medium
- 计划：无
- 原意见：Only the `junit-jupiter` aggregate artifact is version-managed. Any module that later pulls in `junit-jupiter-api`, `-engine`, `-params`, or `junit-platform-*` directly must hard-code versions, which risks version skew between JUnit components. Importing `org.junit.jupiter:junit-bom:${junit.version}` (`<type>pom</type><scope>import</scope>`) manages the whole set consistently and removes that trap.
- 判断：当前统一junit-jupiter aggregate已管理版本，假定未来单引api/engine混版不构成当前依赖冲突，新增时再核对BOM。
- 证据：pom.xml:36

## OCR-2374 · 不采纳

- 位置：pom.xml:74；OCR原严重度：medium
- 计划：无
- 原意见：The release-audit SBOM is generated with `skipAttach=true`, so it is never attached to the project/aggregate so no SBOM is published together with the release artifacts; a release audit that consumes only the released artifacts will therefore find no SBOM. In addition, `includeTestScope=true` adds test-only dependencies to the BOM, which inflates the component list and can produce false positives in vulnerability/license gates, while `outputReactorProjects=false` keeps the reactor's own modules out of the aggregate BOM. If the intent is an auditable, publishable SBOM, use `skipAttach=false` and include the reactor projects (or document explicitly why the audit-only artifact is intentional).
- 判断：本项目候选ZIP明确携带bom.json及frontend/SDK BOM，skipAttach只是禁Maven附属发布；没有正式mvn deploy/Release授权，不存在当前发布遗漏。
- 证据：pom.xml:74

## OCR-2375 · 部分采纳

- 位置：.github/workflows/ci.yml:2；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：同一提交在同一仓库分支上会同时触发 `push` 与 `pull_request`，而工作流没有 `concurrency` 配置；每次都会并行启动 3 个 OS 的矩阵（单腿 60 分钟，含两次 Maven verify 与三浏览器安装），旧提交的检查还会继续占用 runner。建议在 `on` 同级增加按 ref 分组的并发控制，例如 `concurrency: {group: '${{ github.workflow }}-${{ github.ref }}', cancel-in-progress: ${{ github.event_name == 'pull_request' }}}`。
- 判断：工作流缺concurrency，重复推送可并跑三平台消耗资源；采纳按workflow+ref分组取消过期执行，不能把不同host临时keychain并发视作当前互踩。
- 证据：.github/workflows/ci.yml:4

## OCR-2376 · 不采纳

- 位置：.github/workflows/ci.yml:41；OCR原严重度：medium
- 计划：无
- 原意见：在 `set -euo pipefail` 下，`npm audit --audit-level=low` 会把任意 low 级（包括仅 devDependency 的）告警当作失败，并在 `npm run build` 之前直接终止该步骤，导致前端构建产物和后续浏览器矩阵都不执行；第 102 行还会再做一次 `npm --prefix frontend audit --json`，审计被重复。建议删除此处调用，把审计集中到 release gate 并保留 JSON 证据（或至少改为 `--audit-level=high` 并显式记录结果），不要让审计阻断构建。
- 判断：漏洞/审计失败停止后续构建、打包和安装是fail-closed设计；原始audit/OSV报告由always上传，不能要求失败后仍生成候选以凑全证据。可另补失败摘要，但不降低门禁。
- 证据：.github/workflows/ci.yml:41

## OCR-2377 · 不采纳

- 位置：.github/workflows/ci.yml:102；OCR原严重度：high
- 计划：无
- 原意见：`npm --prefix frontend audit --json` 在发现漏洞时返回非零（`--json` 不改变退出码），`scan-osv.py` 同理；在 `set -euo pipefail` 下，这两处失败会让后面的 `build-candidate.py`、`smoke-candidate.py`、`collect-m1059-evidence.py` 完全不执行，`target/*candidate.zip`、`target/candidate-sha256.txt` 和 release evidence 因此缺失。上传步骤虽为 `if: always()`，但 `actions/upload-artifact` 默认 `if-no-files-found: warn`，缺失的关键证据会被静默跳过。建议把“生成候选/证据”与“门禁判定”拆成独立步骤（失败用 `continue-on-error` 或显式记录），并对关键证据设置 `if-no-files-found: error`。
- 判断：漏洞/审计失败停止后续构建、打包和安装是fail-closed设计；原始audit/OSV报告由always上传，不能要求失败后仍生成候选以凑全证据。可另补失败摘要，但不降低门禁。
- 证据：.github/workflows/ci.yml:102

## OCR-2378 · 部分采纳

- 位置：.github/workflows/ci.yml:74；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：`security list-keychains -d user -s "$keychain"` 是替换用户钥匙串搜索列表而非追加，登录钥匙串会被移出；同一 job 后续的 release gate（npm/maven 等可能通过 osxkeychain 取凭据）会受影响，且清理步骤删除钥匙串后没有恢复默认钥匙串和搜索列表。建议保留原列表再追加，例如把 `security list-keychains -d user` 的结果一起传给 `-s`，清理时恢复 `login.keychain-db` 为默认。
- 判断：macOS确替换默认keychain/搜索列表而只删临时keychain，采纳保存并恢复旧值再删除。GitHub托管runner是临时机器，未证实用户keychain被永久破坏。
- 证据：.github/workflows/ci.yml:74

## OCR-2379 · 部分采纳

- 位置：.github/workflows/ci.yml:113；OCR原严重度：medium
- 计划：R6-02 / P3
- 原意见：该步骤使用 `if: always()` 却没有判断钥匙串是否存在；如果创建步骤在 `security create-keychain` 之前失败（例如前面的 python 命令失败），或钥匙串已被删除，`security delete-keychain` 会返回非零，把本应成功的 job 判为失败。建议先恢复默认钥匙串并做存在性判断，或追加 `|| true` 并单独记录清理失败。
- 判断：ci.yml无条件always删除路径，创建前失败会添清理失败；采纳存在性检查与恢复动作，不吞真正清理失败。
- 证据：.github/workflows/ci.yml:113

## OCR-2380 · 部分采纳

- 位置：.github/workflows/ci.yml:86；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：在 `dbus-run-session` 中把密码管道给 `gnome-keyring-daemon --unlock` 后立即运行平台矩阵，`Lock` 之后也立即断言 `locked-service`，都没有等待 secret service 注册或集合锁定状态传播；在慢速 runner 上会出现 `org.freedesktop.secrets` 尚未可用或集合仍为 unlocked 的偶发失败。建议在启动/锁定后轮询 `org.freedesktop.secrets` 的可用性和 login 集合的 `Locked` 属性（带超时与明确错误）再执行对应测试。
- 判断：启动/Lock后无明确服务就绪/collection锁状态确认，missing与locked可能同样不可用；采纳总线owner及属性有界等待，锁定前必须凭据正例，保证区分服务缺失。
- 证据：.github/workflows/ci.yml:86

## OCR-2381 · 部分采纳

- 位置：.github/workflows/native-credentials.yml:60；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：`security default-keychain -d user -s` 与 `security list-keychains -d user -s` 会**覆盖**用户默认 keychain 和整个搜索列表（原 login keychain 被移除）而不保留原值，后续清理步骤只删除临时 keychain 文件，既不恢复默认 keychain 也不恢复搜索列表。触发条件：keychain 准备步骤之后任何步骤失败被跳过清理（cancelled / timeout），或清理时先删文件再未恢复列表，都会让作业结束时搜索列表指向一个不存在的 keychain，后续依赖 Keychain 的工具（codesign、git credential helper 等）会报错或把条目写入已删除的 keychain。建议先备份原默认/搜索列表，清理时先恢复再删除临时 keychain。
- 判断：macOS确替换默认keychain/搜索列表而只删临时keychain，采纳保存并恢复旧值再删除。GitHub托管runner是临时机器，未证实用户keychain被永久破坏。
- 证据：.github/workflows/native-credentials.yml:60

## OCR-2382 · 部分采纳

- 位置：.github/workflows/native-credentials.yml:91；OCR原严重度：medium
- 计划：R6-02 / P2
- 原意见：清理步骤只删除 keychain 文件，未把用户默认 keychain 与搜索列表恢复为备份值（见上一条），删除后搜索列表仍引用不存在的路径。建议在此处先恢复 `-d user` 的默认 keychain 与搜索列表，再删除临时 keychain。
- 判断：macOS确替换默认keychain/搜索列表而只删临时keychain，采纳保存并恢复旧值再删除。GitHub托管runner是临时机器，未证实用户keychain被永久破坏。
- 证据：.github/workflows/native-credentials.yml:91

## OCR-2383 · 部分采纳

- 位置：.github/workflows/native-credentials.yml:73；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：locked-service 用例存在两个可复现缺陷：(1) 启动 gnome-keyring 后没有等待/校验 `org.freedesktop.secrets` 已在会话总线上注册，服务尚未就绪时探针同样返回 `credentialStore == unavailable`、`checks == {'cleanup': False}`，与真正的 locked-service 断言完全一致（见 scripts/test-native-credentials.py 第 24-28 行），因此该用例可能因“服务根本没起来”而假通过；(2) `Secret.Service.Lock` 的回复被丢弃，若返回的是 Prompt 对象路径，集合并未真正上锁，此时解锁状态下探针会成功，用例变成必然失败/随机 flake。建议先轮询 `org.freedesktop.DBus.Peer.Ping` 确认服务就绪并断言 Lock 回复中不含 Prompt（或显式调用 `org.freedesktop.Secret.Prompt.Prompt`），同时让探针区分“服务缺失”和“服务锁定”两种不可用原因。
- 判断：启动/Lock后无明确服务就绪/collection锁状态确认，missing与locked可能同样不可用；采纳总线owner及属性有界等待，锁定前必须凭据正例，保证区分服务缺失。
- 证据：.github/workflows/native-credentials.yml:73

## OCR-2384 · 不采纳

- 位置：.github/workflows/native-credentials.yml:3；OCR原严重度：medium
- 计划：无
- 原意见：push 触发器被硬编码为临时验证分支 `codex/m1054-native-validation`，且没有任何 `pull_request` 触发。合并到默认分支后该工作流将永不自动执行；同时对本文件的修改或 `scripts/test-native-credentials.py` 的修改在 PR 阶段完全不会被验证，工作流会在无人察觉中腐化。建议至少补上默认分支/`pull_request` 触发（或明确标注该文件为一次性验证并配置归档策略）。
- 判断：native-credentials.yml是明确一次性验证分支专项，常规ci.yml已覆盖M10.5.9原生全矩阵；不加PR触发不代表主分支原生完全不测。
- 证据：.github/workflows/native-credentials.yml:4

## OCR-2385 · 部分采纳

- 位置：.github/workflows/native-credentials.yml:6；OCR原严重度：low
- 计划：R6-02 / P3
- 原意见：工作流只有 push/workflow_dispatch，缺少 `concurrency` 控制。连续推送或手动重复触发会并行占用多个 macOS/Windows runner 并各自创建/销毁隔离 keychain、启动 Secret Service 会话；一旦后续改为自托管 runner，并行运行还会互相干扰默认 keychain 与搜索列表状态。建议增加按 ref 分组的 concurrency 并开启 `cancel-in-progress`。
- 判断：工作流缺concurrency，重复推送可并跑三平台消耗资源；采纳按workflow+ref分组取消过期执行，不能把不同host临时keychain并发视作当前互踩。
- 证据：.github/workflows/native-credentials.yml:6

## OCR-2386 · 不采纳

- 位置：.github/workflows/native-credentials.yml:79；OCR原严重度：low
- 计划：无
- 原意见：证据上传使用 `if: always()` 且 `if-no-files-found: error`：当编译或测试在生成 `.build-cache/M10.5.4-native-*.json` 之前失败时，该步骤会再以“找不到文件”报错，产生第二个失败并掩盖真正的根因，增加定位成本。证据类上传建议使用 `if-no-files-found: warn`（`ignore` 亦可），仅在需要强制产物存在时才用 `error`。
- 判断：always上传缺产物报错可明确说明证据未产出，不会覆盖前面步骤原始日志/结论；改warning是策略偏好且可能掩盖意外缺证据，不采纳为缺陷。
- 证据：.github/workflows/native-credentials.yml:79

## OCR-2387 · 部分采纳

- 位置：.editorconfig:14；OCR原严重度：medium
- 计划：R6-03 / P3
- 原意见：CRLF 只对 `mvnw.cmd` 生效，但仓库根目录还存在 `jharness.cmd`（以及后续可能新增的 *.cmd/*.bat）。这些文件会落入 `[*]` 段继承 `end_of_line = lf`：Windows 批处理被强制转成 LF 后，cmd.exe 在 `goto :label`、多行括号块等场景下解析异常，且该配置会让人误以为“脚本已处理”。建议按扩展名统一覆盖，而不是只针对单个文件。
- 判断：cmd行尾策略确不统一；但LF对当前简单批处理不必然失败，web-ui -text有字节保留原因。采纳逐脚本运行验证后统一显式CRLF，保留已绑定历史字节摘要，不盲重写证据。
- 证据：.editorconfig:15

## OCR-2388 · 不采纳

- 位置：.editorconfig:7；OCR原严重度：low
- 计划：无
- 原意见：`[*]` 段全局开启 `trim_trailing_whitespace = true`，会连 Markdown 一起处理。仓库里有大量 `docs/**/*.md`，其中行尾两个空格是 Markdown 的硬换行语法，被自动裁剪后渲染结果会变成同一段落，属于可复现的文档渲染回归。建议为 Markdown 增加覆盖段关闭该选项（同时可保留 YAML/JSON 等的裁剪）。
- 判断：未提供当前Markdown尾双空格被裁导致渲染回归实例；全局trim是仓库既有风格，当前正文大多分段。未来使用硬换行再明确单文件覆盖，不批量改规则。
- 证据：.editorconfig:7

## OCR-2389 · 部分采纳

- 位置：jharness:3；OCR原严重度：high
- 计划：R6-03 / P3
- 原意见：`script_dir` 只按 `$0` 的目录计算，没有解析符号链接。常见安装方式是把启动脚本软链到 `PATH` 目录（如 `/usr/local/bin/jharness -> /opt/j-harness/jharness`），此时 `dirname "$0"` 得到 `/usr/local/bin`，第 4 行的 jar 查找必然失败并误报“先构建”，实际仓库/发行包完好。触发条件：通过软链或 PATH 调用脚本。建议先解析软链再取目录，例如：
- 判断：仓库jharness按$0目录找构建JAR，软链PATH启动确不支持；当前文档用sh ./jharness而分发包另生成launcher。可补明确不支持软链/解析链接，属入口扩展非现有包损坏。
- 证据：jharness:3

## OCR-2390 · 部分采纳

- 位置：jharness:6；OCR原严重度：high
- 计划：R6-03 / P3
- 原意见：当 `JAVA_HOME` 已设置但该目录下没有可执行的 `bin/java`（例如指向 JRE 目录、JDK 安装不完整、只设置了 `JAVA_HOME` 却未更新路径），这里直接 `exec` 会以 127 退出并打印 shell 的 “No such file or directory”，既不回退到 PATH 中的 `java`，也没有说明是 JAVA_HOME 配置问题，脚本的容错与诊断在此中断。触发条件：`JAVA_HOME` 指向无效目录后执行启动器。建议先做 `-x` 校验，失败时给出明确告警并回退，同时对最终 java 不存在的情况给出可读错误：
- 判断：JAVA_HOME坏路径直接exec失败，采纳明确可执行/Java版本诊断，不静默回退到不同JDK以免环境不可复现。
- 证据：jharness:6

## OCR-2391 · 不采纳

- 位置：jharness:5；OCR原严重度：medium
- 计划：无
- 原意见：jar 路径被硬编码为仓库内构建产物布局 `harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar`，且只用 `-f` 判断：文件存在但不可读（权限）或损坏时不会被发现，随后 JVM 以 ClassNotFound/InvalidJar 之类的错误退出；在按发行包/安装目录部署（jar 名称或位置不同）时，脚本永远走 “Build first” 分支，误导用户去构建而实际上不应构建。建议允许通过环境变量覆盖 jar 位置，并补充可读性检查与区分构建缺失/部署缺失的诊断信息。触发条件：以非仓库布局部署、jar 权限不足或文件被截断。
- 判断：仓库启动脚本与候选包启动脚本是不同布局，build-candidate生成lib/jharness.jar入口；不能把仓库jharness直接复制发行目录当产品承诺，JVM也会拒坏JAR。
- 证据：jharness:5

## OCR-2393 · 部分采纳

- 位置：.gitignore:12；OCR原严重度：high
- 计划：R6-02 / P2
- 原意见：敏感文件忽略范围与项目自身约定不一致，存在密钥泄露风险。  1) 只忽略精确文件名 `.env`，未覆盖 `.env.local`/`.env.*.local`/`.env.production` 等变体。触发条件：按本仓库配置用法（如 `apiKeyEnv`、`ecosystem.environment` 注入 key）在仓库根或子模块创建 `.env.local` 写入模型/服务凭据后执行 `git add .`（或 `git add -A`），该文件会被正常纳入提交。可复现验证：`git init tmp && touch .env.local && git add -A` → `.env.local` 处于暂存区。而产品代码自身（如 `harness-policy/.../DefaultPolicyEngine.java` 拒绝 `.env` 与 `.env.*`、`harness-ecosystem/.../SkillsPlugin.java` 拒绝任意 `.env.` 前缀段）明确把 `.env.*` 视作秘密路径，ignore 规则与之不对齐。  2) 未忽略任何私钥/凭据容器（`*.pem`、`*.key`、`*.p12`、`*.pfx`、`*.jks`、`id_rsa*`、`credentials*`）。本仓库存在原生凭据存储与密钥链相关实现/测试（native-credentials workflow、SystemCredentials），在仓库内生成或落地此类材料时同样会被 `git add` 意外提交。  建议同时补全 `.env.*` 变体（并显式放行模板文件）与私钥类模式。
- 判断：.gitignore仅.env未覆盖.env.*，与产品秘密路径范围不同，补环境变体及明确模板例外合理。不要盲目credentials*/*.key广泛屏蔽合法fixture，按真实材料分类加模式并用git check-ignore负例。
- 证据：.gitignore:12

## OCR-2417 · 部分采纳

- 位置：.gitattributes:7；OCR原严重度：medium
- 计划：R6-03 / P3
- 原意见：`-text` 只保留 blob 原始字节，不提供任何行尾保障：若在 Linux（`core.autocrlf=false`）上编辑/提交 `web-ui.cmd`，blob 中的行尾会是 LF，Windows cmd.exe 解析 label/goto 时可能失败；而第 6 行 `mvnw.cmd` 强制 CRLF，两个启动脚本策略不一致。另外仓库根目录的 `jharness.cmd` 没有专属规则，会回落到 `* text=auto`，其 blob 同样被归一化为 LF。触发条件：从 Linux 提交/检出后在 Windows 执行这些 `.cmd`。建议统一按 `*.cmd text eol=crlf` 处理。
- 判断：cmd行尾策略确不统一；但LF对当前简单批处理不必然失败，web-ui -text有字节保留原因。采纳逐脚本运行验证后统一显式CRLF，保留已绑定历史字节摘要，不盲重写证据。
- 证据：.gitattributes:7

## OCR-2418 · 部分采纳

- 位置：.gitattributes:14；OCR原严重度：high
- 计划：R0-02 / P2
- 原意见：证据 JSON 的 `-text` 白名单不完整：`docs/testing/` 下实际存在大量 M8 证据文件（如 `docs/testing/M8-results.json`、`M8.1-results.json`、`M8.2-filter-regression.json`、`M8.4-*-results.json` 等），它们不匹配 `M9-*` / `M10-*` / `M10.5.*`，会回落到第 1 行的 `* text=auto`。`text=auto` 会在提交/检出时按平台归一化行尾（例如 Windows 上 `core.autocrlf=true` 检出为 CRLF，Linux 上为 LF），导致工作区字节与录制哈希不一致，按字节校验 CI 证据时会失败。触发条件：在非录制平台检出或重新提交这些 M8 证据 JSON 后计算哈希。建议统一覆盖整个证据目录，而不是逐个里程碑枚举。
- 判断：M8 JSON不在-text范围但含历史摘要引用，跨平台可能行尾漂移；采纳覆盖所有需字节保留的证据JSON并核对原始blob/hash，不能重算历史哈希伪装原证。
- 证据：.gitattributes:14

## OCR-2419 · 不采纳

- 位置：.gitattributes:12；OCR原严重度：medium
- 计划：无
- 原意见：`-text` 只关闭行尾转换，并未关闭文本 diff/merge（Git 内置 `binary` 宏才等价于 `-text -diff -merge`）。因此这些声明“按字节保留”的路径在 merge/rebase/`git apply` 时仍会按文本处理，可能被写入冲突标记或做文本合并，直接改变原始字节并破坏已记录的哈希。第 12、14-16、19-20 行均存在同样问题。触发条件：合并或变基的提交涉及这些文件。建议改用 `binary` 宏（至少补充 `-merge`）。
- 判断：-text用于禁止行尾转换，不承诺禁止合法merge；binary会隐藏全部文本diff/可审查性，冲突标记也不会自动通过JSON/哈希校验。未有现存误合并证据。
- 证据：.gitattributes:12

## OCR-2420 · 不采纳

- 位置：.gitattributes:18；OCR原严重度：medium
- 计划：无
- 原意见：`text` 会强制把该目录下所有文件当作文本，覆盖第 1 行 `* text=auto` 的二进制自动探测。目前 frontend 下虽只有文本源码，但后续一旦加入 `.png/.ico/.woff2/.wasm` 等二进制资源，其中的 0x0D0A 字节会被当作行尾改写，造成文件损坏且哈希变化；第 21 行 `sdk/** text eol=lf` 同理（`.jar/.so/.dll` 等）。建议改为 `text=auto eol=lf`，只对探测为文本的文件强制 LF。
- 判断：frontend/sdk当前均为受管文本源码且已有来源-text覆盖，未来二进制加入需专属属性；未出现被文本转换损坏的当前资产，不按未来假设重写所有规则。
- 证据：.gitattributes:18

## OCR-2421 · 部分采纳

- 位置：mvnw:153；OCR原严重度：high
- 计划：R6-01 / P2
- 原意见：The Maven wrapper cache/install success is validated only by directory existence: startup trusts any `$MAVEN_HOME` with `[ -d ... ]`, and installation treats `mv ... || [ -d "$MAVEN_HOME" ]` as success. A half-copied/interrupted or concurrent install can leave a directory without `bin/$MVN_CMD`, then `exec` fails before the fallback. The cache key also omits `distributionSha256Sum`. Use `[ -x "$MAVEN_HOME/bin/$MVN_CMD" ]` plus an install-complete marker, or include checksum in the cache key. Copy to a sibling temp path and atomically `mv -T` into place, and lock concurrent installs.
- 判断：Wrapper缓存只查目录且安装race可接受半目录，采纳可执行入口/完成标记与同文件系统原子安装；checksum加入缓存身份合理，不能直接使用非跨平台mv -T。
- 证据：mvnw:153

## OCR-2422 · 采纳

- 位置：mvnw:179；OCR原严重度：high
- 计划：R6-01 / P2
- 原意见：无 `unzip` 时把 URL 改写为 `.tar.gz`，但 `.mvn/wrapper/maven-wrapper.properties` 第4行配置的 `distributionSha256Sum` 是针对 .zip 的，第233/237行仍会用它校验 tar.gz，必然不匹配并以 “your Maven distribution might be compromised” 退出。触发：精简容器/镜像未安装 unzip（此时通常仍有 sha256sum），即使配置完全正确也无法启动，且错误信息误导为供应链问题。建议切换到 tar.gz 时读取/校验对应的 tar.gz 校验和，或在校验和仅适用于 zip 时明确跳过校验并告警（或直接要求安装 unzip）。
- 判断：固定distributionSha256Sum为zip，缺unzip改tar.gz但仍用同摘要必然拒绝。应明确要求unzip或配置对应tar摘要，绝不采纳跳过校验告警的方案。
- 证据：mvnw:179

## OCR-2423 · 部分采纳

- 位置：mvnw:209；OCR原严重度：high
- 计划：R6-01 / P2
- 原意见：生成的 `Downloader` 对任意认证挑战都返回 MVNW_USERNAME/MVNW_PASSWORD，没有检查 `getRequestorType()` 和 `getRequestingHost()`。`HttpURLConnection` 默认跟随重定向；若 distributionUrl 发生跨主机 302、经过恶意/被攻破代理（PROXY 挑战），或使用 http，攻击者返回 401 即可获得仓库凭据。触发：设置 MVNW_USERNAME/MVNW_PASSWORD 下载私有仓库包，且服务器/代理返回重定向或认证挑战。建议只在 `getRequestorType()==SERVER` 且 host 与 distributionUrl 预期主机一致时返回凭据，并限制重定向目标；另外第214行 `openStream()` 没有连接/读取超时、也没有下载大小上限，服务器挂起或无限流会永久阻塞/耗尽磁盘。
- 判断：Java Authenticator无host/requestor约束且openStream无超时预算；采纳精确下载主机/挑战范围和有界读取。私库认证/恶意代理是前提，当前官方公开URL无此凭据输入，不直接P0。
- 证据：mvnw:209

## OCR-2424 · 部分采纳

- 位置：mvnw:175；OCR原严重度：medium
- 计划：R6-01 / P2
- 原意见：`distributionUrl` can include credentials from `MVNW_REPOURL`, and the script exposes it in verbose logs, wget/curl command lines and failure messages, and writes it verbatim to the cache file `mvnw.url` (often mode 0644). Strip userinfo/query before logging or writing the cache file, or avoid persisting credential-bearing URLs; use `umask 077`/`chmod 600` where applicable.
- 判断：MVNW_REPOURL含userinfo时日志/cache会保留URL，采纳拒凭据URL或脱敏/权限最小化；当前配置无userinfo，未证明已发生泄漏。
- 证据：mvnw:175

## OCR-2425 · 部分采纳

- 位置：mvnw:200；OCR原严重度：medium
- 计划：R6-01 / P3
- 原意见：设置 MVNW_USERNAME/MVNW_PASSWORD 后会跳过 wget/curl（第194/197行），只能走 Java 源码下载；若 JAVA_HOME 无效或只有 JRE（`set_java_home` 在第56/74行要求 javac），该函数返回 1，但这个 if/elif 链没有 else，脚本会继续执行校验/解压，最终报 “failed to unzip” 或 “might be compromised” 等误导性错误。另外第222行的 java 下载命令没有 `|| die`，在 `set -e` 下失败会静默退出。触发：仅用 Java 认证下载且缺少可用 javac，或下载返回 401/网络中断。建议补 `else die "..."`，并给第222行加 `|| die "Failed to download $distributionUrl"`。
- 判断：仅Java认证下载但set_java_home失败后无明确else，后续报校验/解压错误；采纳前置清晰失败和下载退出码处理，不改变摘要门禁。
- 证据：mvnw:200

## OCR-2426 · 部分采纳

- 位置：mvnw:141；OCR原严重度：low
- 计划：R6-01 / P3
- 原意见：`${distributionUrl#*"$_MVNW_REPO_PATTERN"}` 在 URL 不含该 pattern 时返回整个原 URL，结果拼成 `$MVNW_REPOURL/org/apache/maven/https://原URL` 这类非法地址。触发：distributionUrl 指向路径不含 `/org/apache/maven/` 的镜像（如某些 Nexus/Artifactory 虚拟仓库），同时设置 MVNW_REPOURL；下载会以 404/协议错误失败。建议先用 `case "$distributionUrl" in *"$_MVNW_REPO_PATTERN"*)` 判断，只有包含 pattern 时才替换，否则显式报错或保留原 URL。
- 判断：MVNW_REPOURL替换假定原URL含Maven坐标路径，无pattern时会拼非法URL；当前固定官方URL满足。采纳显式pattern检查/明确配置拒绝。
- 证据：mvnw:141
