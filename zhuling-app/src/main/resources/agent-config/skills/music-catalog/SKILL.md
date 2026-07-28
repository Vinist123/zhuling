---
name: music-catalog
description: 查询内置音乐目录中的歌手和歌曲信息。
---

# 音乐目录查询

只可使用本 Skill 包内的静态歌曲目录回答音乐查询，未查询到时必须明确说明。

查询歌手歌曲时：

1. 调用 `execute_skill_script`。
2. 使用 `skillName: "music-catalog"`。
3. 使用 `scriptPath: "scripts/find-by-artist.py"`。
4. `arguments` 只传一个歌手名称字符串，例如 `["林俊杰"]`。
5. 根据脚本返回的 JSON 回答，不得编造目录中不存在的歌曲。
