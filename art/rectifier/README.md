# Rectifier

Second character in the NPC fork: an armored district repair guardian, formerly
called Warden. Stone, weathered copper, amber visor/core, lantern, club hammer and tabard.

Open `rectifier-preview.html` in a browser. It includes Rest, Patrol, Repair and
optional Walk poses, independent head movement, night lighting and image export.
Download viewer saves a single HTML file that can be shared and opened offline.

`rectifier.bbmodel` is the editable Blockbench mesh with embedded base texture.
`tools/build_rectifier_assets.py` is authoritative for geometry/UVs; regenerate it
with Python and Pillow. Animations live in RectifierModel.java and preview.js.

See `docs/npc-rectifier-merge-notes.md` for required code changes and compatibility.
The in-game registry ID remains `seedcity:warden`; its display name is Rectifier.

Revision 2: taller legs, wider/deeper chest, a smaller helmet proportion, rebuilt
wrench and an articulated elbow holding the lantern forward. Model height is
about 2.81 blocks; the preview includes a player-height comparison marker.

Revision 3: thicker legs and boots, taller helmet, longer chest and a club hammer
in place of the wrench. Current model: 62 cuboids, nine parts, about 2.94 blocks tall.

Revision 4 follows the close-up reference silhouette, removes shoulder lids, reshapes
the chest/gauntlets and restores long legs. Hammer faces align with the arm swing;
current model has 58 cuboids. The club hammer remains the requested tool even
though the visual reference shows a wrench.

Revision 5: hammer explicitly held head-up, ready to strike. Add reference-inspired
armor fittings, articulated-looking hands, boot trim and patterned tabard; 89 cuboids.

The current version includes a matching back cape using Minecraft avatar cloak
lag and movement-driven lift/sway. View Back or Side, and switch Patrol/Walk to Rest
to see it settle. Current mesh: 86 cuboids in eleven parts.
