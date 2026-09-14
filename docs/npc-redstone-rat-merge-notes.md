# Redstone Rat — approved model

User-requested sixth entity, `seedcity:redstone_rat`, explicitly extending the original
five-mob design. A rat silhouette with ears, a projecting muzzle and nose, four articulated paws,
red-brown fur with redstone flecks, small glowing eyes and a three-piece trailing tail.
The seven-segment backbone retains the requested silverfish influence. Side-to-side waves travel down its body
and tail while moving; idle movement is subtle. Collider: 0.55 wide, 0.30 high.

## Creeper deterrence

RedstoneRatEntity subclasses Cat. Java additionally requires `AvoidRedstoneRatGoal`,
`CreeperRatAvoidanceMixin` and its entry in `seedcity.mixins.json`: priority 1 avoidance
starts within ten blocks and outranks ordinary chasing and natural swelling. It
uses vanilla flee-path navigation and unwinds natural swelling while fleeing.
Manually ignited creepers still explode. Escape space is required. Bedrock retains
native six-block cat-family avoidance without replacing the vanilla creeper JSON.
The rat has no combat goal or silverfish infestation behavior.

Ambient, hurt and death sounds use vanilla silverfish events, at 25% volume. Java
ambient timing is 2,400 ticks plus the engine's random delay; Bedrock uses 120–180
seconds. Taming, sitting and breeding remain disabled.

## Taming is intentionally not implemented

At the user's request, the class registers only swimming, roaming, home-return and
looking goals. It does not register cat sitting, following an owner, temptation,
breeding or bed/chest goals. Player interaction returns PASS and isFood/canMate are
false; offspring creation returns null. Tabor can decide later whether to add
taming and sitting and how those should work. Cat ancestry is used for native
creeper recognition, not as a decision to enable pet behavior.

## Automatic city spawning

Each active city attempts a spawn once every 1,200 ticks while a player is within
64 blocks. `maxRedstoneRats` defaults to two within 24 blocks of its seed; zero
disables automatic spawning, and the implementation clamps the cap to eight.
The search checks solid ground, empty collision space, no water, and already loaded
chunks. It never places or removes blocks. Nearby rats count toward the cap,
including ones from another city; rats outside that local area do not count, as with
village-style local population checks. Existing rats persist. Destroyed/frozen cities
stop this spawning along with their normal tick work.

## Integration files and validation

Pull the entity, spawner, CityState/config changes, entity registration and language
entry as well as RedstoneRatMesh/Model/Renderer and their textures. The artwork alone
cannot supply spawning or creeper avoidance. Existing fork BuildTask and other NPC
integration notes still apply.

Regenerate with `python tools/build_redstone_rat_assets.py`. Editable Blockbench,
geometry JSON, texture and offline viewer are in art/redstone-rat. Server tests check
that a real creeper flees a stationary rat and that spawning honors the cap and disable
setting. The client test renders the registered entity in Minecraft.

Visual revision: body-wave phase now subtracts each segment offset so the wave travels from head to tail. The paws lift during the forward stroke and plant during the rearward stroke. Removed raised stripes and stone coloring. Model has 22 cuboids and 16 parts. Gameplay remains unchanged.

Head/paw revision: tuck paws inward, set fore/aft travel to 0.36 model units and lift to 0.18 during a short forward step; idle paws remain grounded. Raise the entire head 0.75 units, attach muzzle/nose/ears to one neck pivot, and permit up to 65 degrees of upward tracking. A nearby-player look goal is enabled; this does not enable taming or sitting. Preview includes Look up at player.

Stance refinement: paws sit 0.75 model units below the belly, with planted feet still on the floor. The rigid head adds a gentle movement-driven sway and nod on top of player tracking. Paw movement is midway between the original stepping and the restrained shuffle.
