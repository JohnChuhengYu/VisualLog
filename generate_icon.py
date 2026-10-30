import math
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageEnhance
import os
import subprocess

def create_master_icon():
    # Supersampled 2048x2048
    S = 2048
    scale = S / 1024.0

    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    
    # 1. macOS Squircle Background Tile (Centered at 1024x1024, size 824x824 at 1x => 1648x1648 at 2x)
    tile_size = int(824 * scale)
    tile_x = (S - tile_size) // 2
    tile_y = (S - tile_size) // 2 + int(12 * scale)
    corner_radius = int(185 * scale)

    # Multi-layer drop shadow for the macOS tile
    for offset, blur, alpha in [
        (int(24 * scale), int(36 * scale), 45),
        (int(12 * scale), int(16 * scale), 35),
        (int(4 * scale), int(6 * scale), 50),
    ]:
        shadow_mask = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        draw_s = ImageDraw.Draw(shadow_mask)
        draw_s.rounded_rectangle(
            [tile_x, tile_y + offset, tile_x + tile_size, tile_y + tile_size + offset],
            radius=corner_radius,
            fill=(30, 20, 10, alpha)
        )
        shadow_mask = shadow_mask.filter(ImageFilter.GaussianBlur(blur))
        img = Image.alpha_composite(img, shadow_mask)

    # Tile Surface (Warm Craft Paper / Desk Desk Texture with Radial Vignette)
    tile = Image.new("RGBA", (tile_size, tile_size), (0, 0, 0, 0))
    draw_t = ImageDraw.Draw(tile)
    
    # Radial/vertical gradient: #F4EFE6 to #E5DCCF
    for y in range(tile_size):
        t = y / float(tile_size)
        r = int(244 * (1 - t * 0.08))
        g = int(239 * (1 - t * 0.09))
        b = int(230 * (1 - t * 0.10))
        draw_t.line([(0, y), (tile_size, y)], fill=(r, g, b, 255))
        
    # Subtle dot grid on desk tile
    dot_spacing = int(32 * scale)
    for x in range(dot_spacing // 2, tile_size, dot_spacing):
        for y in range(dot_spacing // 2, tile_size, dot_spacing):
            draw_t.ellipse([x - int(1.5 * scale), y - int(1.5 * scale), x + int(1.5 * scale), y + int(1.5 * scale)], fill=(205, 195, 180, 160))

    # Mask tile with squircle
    squircle_mask = Image.new("L", (tile_size, tile_size), 0)
    draw_mask = ImageDraw.Draw(squircle_mask)
    draw_mask.rounded_rectangle([0, 0, tile_size, tile_size], radius=corner_radius, fill=255)
    
    # Border outline on tile
    tile_border = Image.new("RGBA", (tile_size, tile_size), (0, 0, 0, 0))
    draw_b = ImageDraw.Draw(tile_border)
    draw_b.rounded_rectangle([0, 0, tile_size, tile_size], radius=corner_radius, outline=(215, 205, 192, 220), width=int(2.5 * scale))
    tile = Image.alpha_composite(tile, tile_border)

    # Composite tile onto main image
    img.paste(tile, (tile_x, tile_y), squircle_mask)

    # 2. HERO: A Classic Polaroid Card
    # Dimensions in 1x: Width ~560, Height ~680
    card_w = int(580 * scale)
    card_h = int(700 * scale)
    card_corner = int(14 * scale)

    card_layer = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
    draw_c = ImageDraw.Draw(card_layer)

    # Polaroid Card Base (Crisp White Cardstock)
    draw_c.rounded_rectangle([0, 0, card_w, card_h], radius=card_corner, fill=(255, 255, 255, 255))
    draw_c.rounded_rectangle([0, 0, card_w, card_h], radius=card_corner, outline=(220, 212, 200, 240), width=int(2 * scale))

    # Photo Window: Square ~500 x 500 in 1x, marginX ~40, marginTop ~40
    photo_pad_x = int(40 * scale)
    photo_pad_y = int(40 * scale)
    photo_size = card_w - (photo_pad_x * 2)

    photo = Image.new("RGBA", (photo_size, photo_size), (0, 0, 0, 0))
    draw_p = ImageDraw.Draw(photo)

    # Beautiful Warm Vintage Sunrise/Sunset Emulsion (Rich Golden Hour)
    # Deep warm indigo -> sunset amber -> warm peach -> golden yellow
    for y in range(photo_size):
        t = y / float(photo_size)
        if t < 0.35:
            # Twilight navy to coral
            st = t / 0.35
            r = int(55 + (235 - 55) * st)
            g = int(45 + (110 - 45) * st)
            b = int(90 + (100 - 90) * st)
        elif t < 0.7:
            # Coral to warm amber
            st = (t - 0.35) / 0.35
            r = int(235 + (255 - 235) * st)
            g = int(110 + (170 - 110) * st)
            b = int(100 + (80 - 100) * st)
        else:
            # Amber to soft golden cream
            st = (t - 0.7) / 0.3
            r = int(255 + (250 - 255) * st)
            g = int(170 + (225 - 170) * st)
            b = int(80 + (160 - 80) * st)
        draw_p.line([(0, y), (photo_size, y)], fill=(r, g, b, 255))

    # Sun / Optical Aperture Ring in Photo
    sun_cx = photo_size // 2
    sun_cy = int(photo_size * 0.48)
    sun_r = int(photo_size * 0.26)

    # Soft glowing halo
    halo_layer = Image.new("RGBA", (photo_size, photo_size), (0, 0, 0, 0))
    draw_h = ImageDraw.Draw(halo_layer)
    draw_h.ellipse([sun_cx - sun_r * 1.5, sun_cy - sun_r * 1.5, sun_cx + sun_r * 1.5, sun_cy + sun_r * 1.5], fill=(255, 230, 160, 90))
    halo_layer = halo_layer.filter(ImageFilter.GaussianBlur(int(30 * scale)))
    photo = Image.alpha_composite(photo, halo_layer)

    # Vintage Lens Element / Sun Core
    draw_p.ellipse([sun_cx - sun_r, sun_cy - sun_r, sun_cx + sun_r, sun_cy + sun_r], fill=(255, 255, 245, 240), outline=(255, 210, 120, 200), width=int(3 * scale))
    
    # Internal Aperture Rings (Camera Lens Aesthetic)
    for ring_r, alpha in [(int(sun_r * 0.75), 180), (int(sun_r * 0.52), 210), (int(sun_r * 0.3), 230)]:
        draw_p.ellipse([sun_cx - ring_r, sun_cy - ring_r, sun_cx + ring_r, sun_cy + ring_r], outline=(230, 140, 80, alpha), width=int(2.5 * scale))

    # Golden Sparkle (✦) in the center of the lens
    sparkle_len = int(sun_r * 0.35)
    for angle in [0, 45, 90, 135]:
        rad = math.radians(angle)
        dx = sparkle_len * math.cos(rad)
        dy = sparkle_len * math.sin(rad)
        draw_p.line([sun_cx - dx, sun_cy - dy, sun_cx + dx, sun_cy + dy], fill=(255, 255, 255, 240), width=int(2.5 * scale))
    draw_p.ellipse([sun_cx - int(4 * scale), sun_cy - int(4 * scale), sun_cx + int(4 * scale), sun_cy + int(4 * scale)], fill=(255, 255, 255, 255))

    # Inner Photo Window Cutout Border & Inset Shadow
    photo_mask = Image.new("L", (photo_size, photo_size), 0)
    ImageDraw.Draw(photo_mask).rounded_rectangle([0, 0, photo_size, photo_size], radius=int(6 * scale), fill=255)
    
    card_layer.paste(photo, (photo_pad_x, photo_pad_y), photo_mask)

    # Die-cut hairline border around the photo
    draw_c.rounded_rectangle([photo_pad_x, photo_pad_y, photo_pad_x + photo_size, photo_pad_y + photo_size], radius=int(6 * scale), outline=(210, 200, 185, 255), width=int(2 * scale))
    # Top and left inset bevel shadow
    draw_c.line([photo_pad_x, photo_pad_y, photo_pad_x + photo_size, photo_pad_y], fill=(0, 0, 0, 35), width=int(3 * scale))
    draw_c.line([photo_pad_x, photo_pad_y, photo_pad_x, photo_pad_y + photo_size], fill=(0, 0, 0, 30), width=int(3 * scale))

    # Bottom Bezel Text: "VISUAL LOG" with subtle foil stamping
    bezel_y = photo_pad_y + photo_size + int(36 * scale)
    
    # Draw stylized serif label and star
    try:
        font_serif = ImageFont.truetype("/System/Library/Fonts/Supplemental/Georgia Bold.ttf", int(30 * scale))
        font_sub = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", int(16 * scale))
    except Exception:
        font_serif = ImageFont.load_default()
        font_sub = ImageFont.load_default()

    label = "VISUAL LOG"
    bbox = font_serif.getbbox(label)
    lw = bbox[2] - bbox[0]
    lx = (card_w - lw) // 2
    draw_c.text((lx, bezel_y), label, fill=(80, 65, 50, 220), font=font_serif)

    sub_label = "✦ MEMORIES NEVER FADE ✦"
    sub_bbox = font_sub.getbbox(sub_label)
    sub_w = sub_bbox[2] - sub_bbox[0]
    sub_x = (card_w - sub_w) // 2
    draw_c.text((sub_x, bezel_y + int(42 * scale)), sub_label, fill=(160, 145, 130, 200), font=font_sub)

    # 3. Washi Tape (Coral / Terracotta Pin at Top-Right)
    tape_w = int(140 * scale)
    tape_h = int(36 * scale)
    tape = Image.new("RGBA", (tape_w, tape_h), (0, 0, 0, 0))
    draw_tp = ImageDraw.Draw(tape)
    draw_tp.rounded_rectangle([0, 0, tape_w, tape_h], radius=int(3 * scale), fill=(235, 115, 115, 215))
    
    # Semi-translucent fibers on tape
    for x in range(0, tape_w, int(10 * scale)):
        draw_tp.line([(x, 0), (x + int(4 * scale), tape_h)], fill=(255, 255, 255, 30), width=1)

    tape_rot = tape.rotate(12, resample=Image.BICUBIC, expand=True)
    card_layer.paste(tape_rot, (card_w - int(120 * scale), int(6 * scale)), tape_rot)

    # 4. Rotate Polaroid Card by -2.5 degrees and Cast Realistic Shadow
    card_rot = card_layer.rotate(-2.5, resample=Image.BICUBIC, expand=True)

    # Generate multi-layer soft drop shadow for the card
    card_shadow = Image.new("RGBA", card_rot.size, (0, 0, 0, 0))
    card_alpha = card_rot.split()[3]
    card_shadow.paste((35, 25, 15, 60), (0, 0), card_alpha)
    
    card_shadow_blur = card_shadow.filter(ImageFilter.GaussianBlur(int(22 * scale)))
    card_contact_blur = card_shadow.filter(ImageFilter.GaussianBlur(int(8 * scale)))

    # Position on master canvas
    card_cx = S // 2
    card_cy = S // 2 + int(10 * scale)
    card_pos_x = card_cx - card_rot.width // 2
    card_pos_y = card_cy - card_rot.height // 2

    # Paste shadows
    img.paste(card_shadow_blur, (card_pos_x + int(8 * scale), card_pos_y + int(24 * scale)), card_shadow_blur)
    img.paste(card_contact_blur, (card_pos_x + int(3 * scale), card_pos_y + int(8 * scale)), card_contact_blur)

    # Paste Polaroid card
    img.alpha_composite(card_rot, (card_pos_x, card_pos_y))

    # Downsample from 2048 to 1024 with Lanczos for studio antialiasing
    master_1024 = img.resize((1024, 1024), Image.LANCZOS)
    return master_1024

if __name__ == "__main__":
    icon_1024 = create_master_icon()
    os.makedirs("src/main/resources/icons", exist_ok=True)
    
    # Save master 1024 PNG
    master_png_path = "src/main/resources/icons/icon.png"
    icon_1024.save(master_png_path, "PNG")
    print(f"Saved master PNG: {master_png_path}")

    # Generate macOS iconset and .icns
    iconset_dir = "src/main/resources/icons/icon.iconset"
    os.makedirs(iconset_dir, exist_ok=True)
    
    sizes = [16, 32, 64, 128, 256, 512, 1024]
    for s in [16, 32, 128, 256, 512]:
        res_img = icon_1024.resize((s, s), Image.LANCZOS)
        res_img.save(f"{iconset_dir}/icon_{s}x{s}.png")
        res_2x = icon_1024.resize((s * 2, s * 2), Image.LANCZOS)
        res_2x.save(f"{iconset_dir}/icon_{s}x{s}@2x.png")
        
    icns_path = "src/main/resources/icons/icon.icns"
    subprocess.run(["iconutil", "-c", "icns", iconset_dir, "-o", icns_path], check=True)
    print(f"Generated macOS ICNS: {icns_path}")
    
    # Generate Windows .ico
    ico_path = "src/main/resources/icons/icon.ico"
    ico_sizes = [(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)]
    icon_1024.save(ico_path, format="ICO", sizes=ico_sizes)
    print(f"Generated Windows ICO: {ico_path}")

    # Cleanup temp iconset dir
    import shutil
    shutil.rmtree(iconset_dir)
    print("Done!")
