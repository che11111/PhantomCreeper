# 将 BetterModel 使用的 Blockbench 4.10 模型转换为
# ModelEngine 4.1.1 使用的 Blockbench 5.0 分组格式。
#
# ModelEngine 通过名为 hitbox 的骨骼组识别服务器侧命中箱，
# 因此把原始 bounding_box 元素转换为 hitbox 组的普通子元素。

def remove_hitbox_child($uuid):
  walk(
    if type == "array" then
      map(select(. != $uuid))
    else
      .
    end
  );

.meta.format_version = "5.0"
| .meta.model_format = "free"
| .animations |= map(
    if .name == "death" then
      .animators |= with_entries(
        if .value.name == "body" then
          .value.keyframes |= map(
            if .channel == "rotation" then
              .data_points |= map(
                if .x != null then
                  .x = (((.x | tonumber) * -1) | tostring)
                else
                  .
                end
              )
            else
              .
            end
          )
        else
          .
        end
      )
    else
      .
    end
  )
| .elements |= map(
    if .type == "bounding_box" then
      .type = "cube"
      | .box_uv = true
      | .render_order = "default"
      | .allow_mirror_modeling = true
    else
      .
    end
  )
| .groups += [
    {
      "name": "hitbox",
      "origin": [0, 24, 0],
      "rotation": [0, 0, 0],
      "export": true,
      "isOpen": true,
      "uuid": "08f5f408-71e0-49c7-b3ed-5acb802f4c46"
    }
  ]
| .outliner |= remove_hitbox_child("7cfbf2fe-f25e-41e2-9a0c-f66b7f117e9a")
| .outliner += [
    {
      "uuid": "08f5f408-71e0-49c7-b3ed-5acb802f4c46",
      "isOpen": true,
      "children": ["7cfbf2fe-f25e-41e2-9a0c-f66b7f117e9a"]
    }
  ]
