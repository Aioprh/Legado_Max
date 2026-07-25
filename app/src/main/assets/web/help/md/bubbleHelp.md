# 段评气泡管理帮助

段评气泡用于把书源规则里的 `dp` 图片转成原生 SVG 气泡，支持自定义 SVG 模板和日夜间配色。

---

## 接入格式

在书源规则中使用以下 `img` 标签格式：

```
<img src="dp:12,{"pclick":"...","status":"normal"}">
```

### 参数说明

| 参数 | 说明 |
|------|------|
| `dp:` 后的数字 | 段评数量，会替换 SVG 模板中的 `${num}` 占位符 |
| `status` | 可选。`normal` 使用常规色，`emphasis` 使用强调色；不写时默认 `normal` |

---

## SVG 模板

SVG 模板支持两个占位符：

| 占位符 | 含义 |
|--------|------|
| `${color}` | 气泡颜色，根据日夜间模式和 `status` 自动替换 |
| `${num}` | 段评数量，来自 `dp:` 后的数字 |

### 默认模板示例

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <path d="M44 48 Q48 48 48 44 L48 20 Q16 16 20 16 L20 16 Q16 16 16 20 L16 24 S16 28 10 30 Q6 32 10 34 Q16 36 16 38 L16 44 Q16 48 20 48 Z"
        fill="none" stroke="${color}" stroke-width="3.2"
        stroke-linejoin="round" stroke-linecap="round"/>
  <text x="32" y="32" dy=".35em" text-anchor="middle"
        font-family="sans-serif" font-size="15" font-weight="600"
        fill="${color}">${num}</text>
</svg>
```

---

## 配色说明

每个气泡包包含四组颜色配置：

| 配置项 | 含义 |
|--------|------|
| 日间常规色 | 日间模式下 `status="normal"` 时使用的颜色 |
| 日间强调色 | 日间模式下 `status="emphasis"` 时使用的颜色 |
| 夜间常规色 | 夜间模式下 `status="normal"` 时使用的颜色 |
| 夜间强调色 | 夜间模式下 `status="emphasis"` 时使用的颜色 |

---

## 操作说明

- **内置气泡**：只读，不可编辑或删除，可直接应用。
- **自定义气泡**：点击「添加」可手动创建，支持编辑 SVG 模板、缩放比例和配色。
- **导入 Zip**：点击「添加」→「导入 Zip」可从 zip 文件导入气泡包（zip 中需包含 `bubble.json` 配置文件）。
- **缩放比例**：范围 0.5 ~ 1.5，用于调整气泡整体大小。
- **应用**：点击列表项或「应用」按钮切换当前使用的气泡包。
