#!/usr/bin/env python3
"""Render a looping PhantomCreeper logo GIF from phantom_creeper.bbmodel.

The renderer follows the model's group hierarchy and uses the existing ``flap``
animation. A subtle head breath is layered on top so the logo does not need a
second, hand-authored animation in the source model.

Requires: Pillow and NumPy.
"""

from __future__ import annotations

import argparse
import base64
import io
import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import numpy as np
from PIL import Image


def _rotation(degrees: Iterable[float]) -> np.ndarray:
    x, y, z = (math.radians(float(value)) for value in degrees)
    cx, sx = math.cos(x), math.sin(x)
    cy, sy = math.cos(y), math.sin(y)
    cz, sz = math.cos(z), math.sin(z)
    rx = np.array(((1, 0, 0), (0, cx, -sx), (0, sx, cx)), dtype=np.float64)
    ry = np.array(((cy, 0, sy), (0, 1, 0), (-sy, 0, cy)), dtype=np.float64)
    rz = np.array(((cz, -sz, 0), (sz, cz, 0), (0, 0, 1)), dtype=np.float64)
    matrix = np.eye(4, dtype=np.float64)
    matrix[:3, :3] = rz @ ry @ rx
    return matrix


def _translation(xyz: Iterable[float]) -> np.ndarray:
    matrix = np.eye(4, dtype=np.float64)
    matrix[:3, 3] = tuple(float(value) for value in xyz)
    return matrix


def _normalize(vector: np.ndarray) -> np.ndarray:
    length = np.linalg.norm(vector)
    return vector / length if length else vector


def _look_at(camera: np.ndarray, target: np.ndarray, up: np.ndarray) -> np.ndarray:
    forward = _normalize(target - camera)
    right = _normalize(np.cross(forward, up))
    corrected_up = np.cross(right, forward)
    view = np.eye(4, dtype=np.float64)
    view[0, :3] = right
    view[1, :3] = corrected_up
    view[2, :3] = forward
    view[:3, 3] = -view[:3, :3] @ camera
    return view


def _sample_keyframes(keyframes: list[dict], channel: str, time: float) -> np.ndarray | None:
    frames = [keyframe for keyframe in keyframes if keyframe["channel"] == channel]
    if not frames:
        return None
    frames.sort(key=lambda item: float(item["time"]))
    before = frames[0]
    after = frames[-1]
    if time <= float(before["time"]):
        point = before["data_points"][0]
        return np.array((float(point["x"]), float(point["y"]), float(point["z"])))
    if time >= float(after["time"]):
        point = after["data_points"][0]
        return np.array((float(point["x"]), float(point["y"]), float(point["z"])))

    for left, right in zip(frames, frames[1:]):
        left_time = float(left["time"])
        right_time = float(right["time"])
        if left_time <= time <= right_time:
            factor = (time - left_time) / max(right_time - left_time, 1e-9)
            a = left["data_points"][0]
            b = right["data_points"][0]
            va = np.array((float(a["x"]), float(a["y"]), float(a["z"])))
            vb = np.array((float(b["x"]), float(b["y"]), float(b["z"])))
            return va + (vb - va) * factor
    raise RuntimeError("Could not interpolate animation keyframes")


def _load_texture(model: dict, model_path: Path) -> Image.Image:
    source = model["textures"][0].get("source")
    if isinstance(source, str) and source.startswith("data:image/"):
        payload = source.split(",", 1)[1]
        return Image.open(io.BytesIO(base64.b64decode(payload))).convert("RGBA")

    relative_path = model["textures"][0].get("path")
    if relative_path:
        texture_path = (model_path.parent / relative_path).resolve()
        return Image.open(texture_path).convert("RGBA")
    raise ValueError("The Blockbench texture is neither embedded nor linked")


def _find_parents(outliner: list[dict], parent: str | None = None) -> dict[str, str | None]:
    parents: dict[str, str | None] = {}
    for item in outliner:
        if isinstance(item, str):
            parents.setdefault(item, parent)
            continue
        uuid = item["uuid"]
        parents[uuid] = parent
        _walk_outliner(item.get("children", []), uuid, parents)
    return parents


def _walk_outliner(items: list, parent: str, parents: dict[str, str | None]) -> None:
    for item in items:
        if isinstance(item, str):
            parents.setdefault(item, parent)
            continue
        uuid = item["uuid"]
        parents[uuid] = parent
        _walk_outliner(item.get("children", []), uuid, parents)


@dataclass(frozen=True)
class Face:
    corners: tuple[tuple[float, float, float], ...]
    normal: tuple[float, float, float]
    uv: tuple[float, float, float, float]


def _cube_faces(cube: dict) -> list[Face]:
    x0, y0, z0 = (float(value) for value in cube["from"])
    x1, y1, z1 = (float(value) for value in cube["to"])
    x0, x1 = sorted((x0, x1))
    y0, y1 = sorted((y0, y1))
    z0, z1 = sorted((z0, z1))

    # Corners run clockwise when viewed from outside the face. The UVs are
    # taken directly from Blockbench's per-face texture rectangles.
    corners = {
        "north": ((x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)),
        "south": ((x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)),
        "east": ((x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)),
        "west": ((x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)),
        "up": ((x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)),
        "down": ((x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)),
    }
    normals = {
        "north": (0, 0, -1),
        "south": (0, 0, 1),
        "east": (1, 0, 0),
        "west": (-1, 0, 0),
        "up": (0, 1, 0),
        "down": (0, -1, 0),
    }

    faces: list[Face] = []
    for name, face_data in cube.get("faces", {}).items():
        if "uv" not in face_data:
            continue
        faces.append(
            Face(
                corners=corners[name],
                normal=normals[name],
                uv=tuple(float(value) for value in face_data["uv"]),
            )
        )
    return faces


class Renderer:
    def __init__(
        self,
        model: dict,
        texture: Image.Image,
        size: int,
        supersample: int,
        camera_position: tuple[float, float, float],
        camera_target: tuple[float, float, float],
        field_of_view: float,
        head_motion: float,
    ) -> None:
        self.model = model
        self.texture = np.asarray(texture)
        self.texture_height, self.texture_width = self.texture.shape[:2]
        self.size = size
        self.work_size = size * supersample
        self.camera_position = np.array(camera_position, dtype=np.float64)
        self.camera_target = np.array(camera_target, dtype=np.float64)
        self.view = _look_at(self.camera_position, self.camera_target, np.array((0.0, 1.0, 0.0)))
        self.focal = self.work_size / (2.0 * math.tan(math.radians(field_of_view) / 2.0))
        self.head_motion = head_motion
        self.light = _normalize(np.array((-0.35, 0.82, -0.72), dtype=np.float64))

        self.groups = {group["uuid"]: group for group in model.get("groups", [])}
        self.parents = _find_parents(model.get("outliner", []))
        self.cubes = {cube["uuid"]: cube for cube in model.get("elements", []) if cube.get("type") == "cube"}
        self.flap = next(
            (animation for animation in model.get("animations", []) if animation.get("name") == "flap"),
            None,
        )
        if self.flap is None:
            raise ValueError("The model does not contain the required 'flap' animation")

    def _animated_rotation(self, group_uuid: str, time: float) -> np.ndarray:
        animator = self.flap.get("animators", {}).get(group_uuid)
        if not animator:
            return np.zeros(3, dtype=np.float64)
        value = _sample_keyframes(animator.get("keyframes", []), "rotation", time)
        return value if value is not None else np.zeros(3)

    def _group_matrix(self, group_uuid: str, time: float, cache: dict[str, np.ndarray]) -> np.ndarray:
        if group_uuid in cache:
            return cache[group_uuid]
        group = self.groups[group_uuid]
        parent_uuid = self.parents.get(group_uuid)
        parent = self._group_matrix(parent_uuid, time, cache) if parent_uuid in self.groups else np.eye(4)
        pivot = np.array(group.get("origin", (0, 0, 0)), dtype=np.float64)
        base_rotation = group.get("rotation") or (0, 0, 0)
        rotation = np.array(base_rotation, dtype=np.float64) + self._animated_rotation(group_uuid, time)
        position = np.zeros(3, dtype=np.float64)

        if group.get("name") == "h_head":
            phase = time * math.tau
            rotation += np.array(
                (
                    -3.0 * math.sin(phase),
                    1.35 * math.sin(phase + 0.45),
                    -0.55 * math.sin(phase * 2.0 + 0.25),
                )
            ) * self.head_motion
            position[1] += 0.22 * math.sin(phase + 0.8) * self.head_motion

        local = (
            _translation(pivot + position)
            @ _rotation(rotation)
            @ _translation(-pivot)
        )
        matrix = parent @ local
        cache[group_uuid] = matrix
        return matrix

    def _render_face(
        self,
        face: Face,
        matrix: np.ndarray,
        rgb: np.ndarray,
        alpha: np.ndarray,
        depth: np.ndarray,
    ) -> None:
        corners = np.array(face.corners, dtype=np.float64)
        homogeneous = np.concatenate((corners, np.ones((4, 1))), axis=1)
        world = (matrix @ homogeneous.T).T[:, :3]
        view = (self.view @ np.concatenate((world, np.ones((4, 1))), axis=1).T).T[:, :3]
        near = view[:, 2] > 1e-5
        if not near.all():
            return

        screen_x = self.work_size / 2.0 + self.focal * view[:, 0] / view[:, 2]
        screen_y = self.work_size / 2.0 - self.focal * view[:, 1] / view[:, 2]
        screen = np.stack((screen_x, screen_y), axis=1)

        normal = _normalize((matrix[:3, :3] @ np.array(face.normal, dtype=np.float64)))
        brightness = 0.63 + 0.37 * max(0.0, float(np.dot(normal, self.light)))
        u1, v1, u2, v2 = face.uv
        u_left, u_right = min(u1, u2), max(u1, u2)
        v_top, v_bottom = min(v1, v2), max(v1, v2)
        # The side-face corner lists above run bottom-left, bottom-right,
        # top-right, top-left. Keep the texture's original U/V direction so a
        # creeper face is not accidentally rendered upside down.
        corner_uvs = np.array(
            (
                (u1, v2),
                (u2, v2),
                (u2, v1),
                (u1, v1),
            ),
            dtype=np.float64,
        )

        for indices in ((0, 1, 2), (0, 2, 3)):
            points = screen[list(indices)]
            uv = corner_uvs[list(indices)]
            z_values = view[list(indices), 2]
            self._rasterize_triangle(points, uv, z_values, brightness, rgb, alpha, depth)

    def _rasterize_triangle(
        self,
        points: np.ndarray,
        uv: np.ndarray,
        z_values: np.ndarray,
        brightness: float,
        rgb: np.ndarray,
        alpha_buffer: np.ndarray,
        depth: np.ndarray,
    ) -> None:
        x0, y0 = points[0]
        x1, y1 = points[1]
        x2, y2 = points[2]
        denominator = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(denominator) < 1e-8:
            return

        min_x = max(0, math.floor(min(x0, x1, x2)))
        max_x = min(self.work_size - 1, math.ceil(max(x0, x1, x2)))
        min_y = max(0, math.floor(min(y0, y1, y2)))
        max_y = min(self.work_size - 1, math.ceil(max(y0, y1, y2)))
        if min_x > max_x or min_y > max_y:
            return

        xs = np.arange(min_x, max_x + 1, dtype=np.float64) + 0.5
        ys = np.arange(min_y, max_y + 1, dtype=np.float64) + 0.5
        grid_x, grid_y = np.meshgrid(xs, ys)
        w0 = ((y1 - y2) * (grid_x - x2) + (x2 - x1) * (grid_y - y2)) / denominator
        w1 = ((y2 - y0) * (grid_x - x2) + (x0 - x2) * (grid_y - y2)) / denominator
        w2 = 1.0 - w0 - w1
        inside = (w0 >= -1e-7) & (w1 >= -1e-7) & (w2 >= -1e-7)
        if not np.any(inside):
            return

        inv_z = w0 / z_values[0] + w1 / z_values[1] + w2 / z_values[2]
        face_depth = 1.0 / inv_z
        local_depth = depth[min_y : max_y + 1, min_x : max_x + 1]
        visible = inside & (face_depth > 0.0) & (face_depth < local_depth)
        if not np.any(visible):
            return

        u = (w0 * uv[0, 0] / z_values[0] + w1 * uv[1, 0] / z_values[1] + w2 * uv[2, 0] / z_values[2]) / inv_z
        v = (w0 * uv[0, 1] / z_values[0] + w1 * uv[1, 1] / z_values[1] + w2 * uv[2, 1] / z_values[2]) / inv_z
        tex_x = np.clip(np.floor(u), 0, self.texture_width - 1).astype(np.intp)
        tex_y = np.clip(np.floor(v), 0, self.texture_height - 1).astype(np.intp)
        colors = self.texture[tex_y, tex_x].astype(np.float64)
        textured = colors[..., :3] * brightness
        alpha = colors[..., 3]
        visible &= alpha > 8

        local_rgb = rgb[min_y : max_y + 1, min_x : max_x + 1]
        local_alpha = alpha_buffer[min_y : max_y + 1, min_x : max_x + 1]
        local_rgb[visible] = np.clip(textured[visible], 0.0, 255.0).astype(np.uint8)
        local_alpha[visible] = 255
        local_depth[visible] = face_depth[visible]

    def render(self, time: float, background: tuple[int, int, int, int] | None) -> Image.Image:
        initial = np.array(background if background is not None else (0, 0, 0, 0), dtype=np.uint8)
        canvas = np.empty((self.work_size, self.work_size, 4), dtype=np.uint8)
        canvas[...] = initial
        rgb = canvas[..., :3]
        alpha = canvas[..., 3]
        depth = np.full((self.work_size, self.work_size), np.inf, dtype=np.float64)
        cache: dict[str, np.ndarray] = {}

        for cube_uuid, cube in self.cubes.items():
            parent_uuid = self.parents.get(cube_uuid)
            matrix = (
                self._group_matrix(parent_uuid, time, cache)
                if parent_uuid in self.groups
                else np.eye(4)
            )
            for face in _cube_faces(cube):
                self._render_face(face, matrix, rgb, alpha, depth)

        image = Image.fromarray(canvas)
        if background is not None:
            image = image.resize((self.size, self.size), Image.Resampling.LANCZOS).convert("RGB")
        else:
            image = image.resize((self.size, self.size), Image.Resampling.LANCZOS).convert("RGBA")
        return image


def _quantized_frames(frames: list[Image.Image], transparent: bool) -> list[Image.Image]:
    if not transparent:
        return [
            frame.quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
            for frame in frames
        ]

    # Build a shared palette from visible pixels so colors do not shimmer while
    # the model moves. The final index is reserved for transparency.
    samples = []
    for frame in frames:
        array = np.asarray(frame)
        samples.append(array[array[..., 3] > 96][:, :3])
    sample_rgb = np.concatenate(samples, axis=0)
    if len(sample_rgb) > 250_000:
        stride = math.ceil(len(sample_rgb) / 250_000)
        sample_rgb = sample_rgb[::stride]
    palette_source = Image.fromarray(sample_rgb.reshape(-1, 1, 3))
    palette_image = palette_source.quantize(
        colors=255,
        method=Image.Quantize.MEDIANCUT,
        dither=Image.Dither.NONE,
    )
    palette = (palette_image.getpalette() or [])[: 255 * 3]
    palette.extend((255, 0, 255))

    output: list[Image.Image] = []
    for frame in frames:
        array = np.asarray(frame)
        quantized = Image.fromarray(array[..., :3]).quantize(
            palette=palette_image,
            dither=Image.Dither.NONE,
        )
        indexed = np.asarray(quantized).copy()
        indexed[array[..., 3] < 96] = 255
        transparent_frame = Image.frombytes("P", quantized.size, indexed.tobytes())
        transparent_frame.putpalette(palette)
        transparent_frame.info["transparency"] = 255
        output.append(transparent_frame)
    return output


def _save_gif(frames: list[Image.Image], path: Path, fps: int, transparent: bool) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    indexed = _quantized_frames(frames, transparent)
    options = {
        "save_all": True,
        "append_images": indexed[1:],
        "duration": round(1000 / fps),
        "loop": 0,
        "disposal": 2,
        "optimize": True,
    }
    if transparent:
        options["transparency"] = 255
    indexed[0].save(path, **options)


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, default=root / "phantom_creeper.bbmodel")
    parser.add_argument("--output", type=Path, default=root / "img" / "logo.gif")
    parser.add_argument("--white-output", type=Path, default=root / "img" / "logo-white.gif")
    parser.add_argument("--size", type=int, default=512)
    parser.add_argument("--fps", type=int, default=24)
    parser.add_argument("--frames", type=int, default=24)
    parser.add_argument("--supersample", type=int, default=2)
    # This low front-quarter view matches img/logo-1024x.png: the head is at
    # the upper left, the tail at the lower right, and the wing planes form the
    # same diagonal silhouette as the existing static logo.
    parser.add_argument("--camera", type=float, nargs=3, default=(-58.0, -20.0, -68.0))
    parser.add_argument("--target", type=float, nargs=3, default=(0.0, 27.0, 2.0))
    # Tighter crop than the source logo while leaving every animated wing
    # position safely inside the square canvas.
    parser.add_argument("--fov", type=float, default=25.0)
    parser.add_argument("--head-motion", type=float, default=1.0)
    parser.add_argument("--poster", type=Path, default=root / "img" / "logo-poster.png")
    args = parser.parse_args()

    model = json.loads(args.model.read_text(encoding="utf-8"))
    texture = _load_texture(model, args.model)
    renderer = Renderer(
        model=model,
        texture=texture,
        size=args.size,
        supersample=args.supersample,
        camera_position=tuple(args.camera),
        camera_target=tuple(args.target),
        field_of_view=args.fov,
        head_motion=args.head_motion,
    )

    transparent_frames: list[Image.Image] = []
    white_frames: list[Image.Image] = []
    for frame_index in range(args.frames):
        # The source flap animation is one second long. Sampling its normalized
        # phase makes the GIF loop cleanly at any requested GIF frame rate.
        phase = frame_index / args.frames
        transparent_frames.append(renderer.render(phase, None))
        white_frames.append(renderer.render(phase, (255, 255, 255, 255)))

    _save_gif(transparent_frames, args.output, args.fps, transparent=True)
    _save_gif(white_frames, args.white_output, args.fps, transparent=False)
    args.poster.parent.mkdir(parents=True, exist_ok=True)
    white_frames[0].save(args.poster, format="PNG", optimize=True)
    print(f"Wrote {args.output} ({args.size}x{args.size}, {args.frames} frames at {args.fps} fps)")
    print(f"Wrote {args.white_output}")
    print(f"Wrote {args.poster}")


if __name__ == "__main__":
    main()
