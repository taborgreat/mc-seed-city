# Builder

First character in the NPC fork: compact stone-and-weathered-copper construction
worker with cyan eyes, a leather belt and pouches, a belt hammer, material-load
block and a free left hand. Based on the user's approved five-character
reference, specifically its leftmost character.

Open `builder-preview.html` in a browser to rotate and inspect the actual model.
It runs offline. `builder.bbmodel` is the editable Blockbench mesh and embeds its
texture. The preview approximates lighting; the Fabric client-test captures are
actual Minecraft screenshots.

Use Walk to preview the optional walking animation. The mod still uses its existing
flight navigation. The tablet and its grip have been removed.
Send the HTML file directly to a friend, or use Download viewer to save a copy.
It includes its model, textures and renderer; no server or Minecraft is required.
Save image downloads the current camera view as a PNG.

The final revision separates overlapping armor faces to fix depth flicker. The
geometry generator checks coplanar overlapping outward faces in each rigid part.
The model has 52 cuboids in seven parts; the free hand rests naturally while flying
and swings opposite the other arm during walking.
