def keyframe($time; $x; $y; $z; $uuid):
  {
    channel: "rotation",
    data_points: [{x: ($x | tostring), y: ($y | tostring), z: ($z | tostring)}],
    uuid: $uuid,
    time: $time,
    color: -1,
    interpolation: "linear"
  };

def position_keyframe($time; $x; $y; $z; $uuid):
  {
    channel: "position",
    data_points: [{x: ($x | tostring), y: ($y | tostring), z: ($z | tostring)}],
    uuid: $uuid,
    time: $time,
    color: -1,
    interpolation: "linear"
  };

def scale_keyframe($time; $scale; $uuid):
  {
    channel: "scale",
    data_points: [{
      x: ($scale | tostring),
      y: ($scale | tostring),
      z: ($scale | tostring),
      uniform: true
    }],
    uuid: $uuid,
    time: $time,
    color: -1,
    interpolation: "linear"
  };

.groups |= map(if .name == "head" then .name = "h_head" else . end)

| .elements |= map(select(.uuid != "7cfbf2fe-f25e-41e2-9a0c-f66b7f117e9a"))
| walk(if type == "array" then map(select(. != "7cfbf2fe-f25e-41e2-9a0c-f66b7f117e9a")) else . end)

| .elements += [
  {
    name: "bounding_box",
    locked: false,
    export: true,
    scope: 0,
    from: [-12, 18, -16],
    to: [12, 32, 16],
    color: 2,
    type: "bounding_box",
    uuid: "7cfbf2fe-f25e-41e2-9a0c-f66b7f117e9a"
  }
]

| .outliner[0].children += [
  "7cfbf2fe-f25e-41e2-9a0c-f66b7f117e9a"
]

| .animations = [{
  uuid: "0f69ddb1-2f75-4c59-a4d2-f0d10b905771",
  name: "flap",
  loop: "loop",
  override: false,
  length: 1.0,
  snapping: 24,
  selected: false,
  group_name: "",
  anim_time_update: "",
  blend_weight: "",
  start_delay: "",
  loop_delay: "",
  animators: {
    "17815014-7214-4d88-8004-0c511df20f05": {
      name: "left_wing_base",
      type: "bone",
      rotation_global: false,
      quaternion_interpolation: false,
      keyframes: [
        keyframe(0.0; 0; 0; 0; "238f0c4c-1444-4b36-ae0c-11e9b054a87f"),
        keyframe(0.25; 0; 0; 22; "c2b8e1bd-8b07-4a5c-a121-9e62ca5cdc04"),
        keyframe(0.5; 0; 0; 0; "3e6c6b87-eac0-4f56-9bc9-5319674b5680"),
        keyframe(0.75; 0; 0; -14; "c581b22c-95b7-44db-ad76-e298b44cb9cf"),
        keyframe(1.0; 0; 0; 0; "518b5d80-190f-405d-947e-79a8f4a75b0e")
      ]
    },
    "b9515552-fbfa-48dc-8c47-f066b0a27d55": {
      name: "right_wing_base",
      type: "bone",
      rotation_global: false,
      quaternion_interpolation: false,
      keyframes: [
        keyframe(0.0; 0; 0; 0; "da19ffb1-7452-4a4b-82d7-40106a01741c"),
        keyframe(0.25; 0; 0; -22; "fa2d58ba-c02a-4fa2-b451-aa17e1d7651c"),
        keyframe(0.5; 0; 0; 0; "6ba89d70-c529-4470-934c-57232d32a873"),
        keyframe(0.75; 0; 0; 14; "08fbb064-fbc1-4d1d-9e8e-6702970490f2"),
        keyframe(1.0; 0; 0; 0; "dde3a646-1d96-4614-991e-07a5d3638c9b")
      ]
    },
    "e674cc25-74fb-4598-97e9-f70988f4c33b": {
      name: "tail_base",
      type: "bone",
      rotation_global: false,
      quaternion_interpolation: false,
      keyframes: [
        keyframe(0.0; 0; 0; 0; "af74a9d6-96a1-4a84-9c83-7da9e9b49af1"),
        keyframe(0.5; 0; 5; 0; "23e33c22-f588-4bbb-af86-f837c3ed38b7"),
        keyframe(1.0; 0; 0; 0; "769d18b1-935c-4452-aea4-f3a26a344ae9")
      ]
    }
  }
}, {
  uuid: "17cdf830-259c-4fff-b265-d5880f80b808",
  name: "death",
  loop: "once",
  override: false,
  length: 0.8,
  snapping: 24,
  selected: false,
  group_name: "",
  anim_time_update: "",
  blend_weight: "",
  start_delay: "",
  loop_delay: "",
  animators: {
    "288b79a0-32be-47f9-8d41-090526234056": {
      name: "body",
      type: "bone",
      rotation_global: false,
      quaternion_interpolation: false,
      keyframes: [
        keyframe(0.0; 0; 0; 0; "87a9b090-dad5-4dc9-a83a-4f542c6b6c37"),
        keyframe(0.25; 22; 0; -6; "ac971858-e0a6-407b-ae31-042d486c99fa"),
        keyframe(0.55; 58; 0; -12; "c7fa6897-7e89-46c9-a388-e830b14e1dbc"),
        keyframe(0.8; 82; 0; -16; "9f4024bc-9630-4f76-8a28-bf24c8add330"),
        position_keyframe(0.0; 0; 0; 0; "d2c53f1a-fc6f-4ab8-b681-96fa830b90f9"),
        position_keyframe(0.25; 0; -4; 0; "bf5fb98c-90da-40dd-af9c-4390992cb84a"),
        position_keyframe(0.55; 0; -12; 0; "9d6a5fb8-c3a1-420e-ae38-0a4d2ac1977a"),
        position_keyframe(0.8; 0; -24; 0; "b1a6d812-f18c-47e9-b9da-f3f5ef140b71")
      ]
    },
    "17815014-7214-4d88-8004-0c511df20f05": {
      name: "left_wing_base",
      type: "bone",
      rotation_global: false,
      quaternion_interpolation: false,
      keyframes: [
        keyframe(0.0; 0; 0; 0; "d76705da-35da-4297-8c13-e8c509530e0c"),
        keyframe(0.8; 0; 0; 28; "b32b88b0-37bb-46b2-b5e6-eaa4d5b57bd8")
      ]
    },
    "b9515552-fbfa-48dc-8c47-f066b0a27d55": {
      name: "right_wing_base",
      type: "bone",
      rotation_global: false,
      quaternion_interpolation: false,
      keyframes: [
        keyframe(0.0; 0; 0; 0; "3c95edc8-c1a9-4c68-bd08-47a1c89bb8e4"),
        keyframe(0.8; 0; 0; -28; "95f39b02-a347-42ed-b1cb-b4d2be58bd42")
      ]
    },
    "e674cc25-74fb-4598-97e9-f70988f4c33b": {
      name: "tail_base",
      type: "bone",
      rotation_global: false,
      quaternion_interpolation: false,
      keyframes: [
        keyframe(0.0; 0; 0; 0; "36b29cb2-fb05-43ff-9b4d-582f0c293451"),
        keyframe(0.8; -28; 0; 0; "7b0c74dd-d629-4cc2-828a-8f6f7d5e08ee")
      ]
    }
  }
}]
