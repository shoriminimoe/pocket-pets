# Changelog

## [0.4.0](https://github.com/shoriminimoe/pocket-pets/compare/v0.3.0...v0.4.0) (2026-05-12)


### Features

* add previewCenterFor helper and drag-preview size/lift constants ([c49ddc7](https://github.com/shoriminimoe/pocket-pets/commit/c49ddc7f09ab1b7fc0faf1e260c556e45c8243cd))
* brush grooming tool raises cleanliness ([aded146](https://github.com/shoriminimoe/pocket-pets/commit/aded146677d2db7d999f7f04251a49d3b8fa6965))
* brush grooming tool raises cleanliness via drag-onto-cat ([4ddbcd4](https://github.com/shoriminimoe/pocket-pets/commit/4ddbcd4f87f1e74939dc8f9b782ee309bb273cf9)), closes [#13](https://github.com/shoriminimoe/pocket-pets/issues/13)
* draggable food bowl ([#14](https://github.com/shoriminimoe/pocket-pets/issues/14)) ([0e3c045](https://github.com/shoriminimoe/pocket-pets/commit/0e3c045c41db57da5dc01d7497a2bb8546cd201f))
* draggable food bowl persists across recomposition ([#14](https://github.com/shoriminimoe/pocket-pets/issues/14)) ([c5e23f3](https://github.com/shoriminimoe/pocket-pets/commit/c5e23f39177d3514589f197f4cf07ccf0ff40897))
* enlarge drag preview above finger ([#16](https://github.com/shoriminimoe/pocket-pets/issues/16)) ([c0ac4bf](https://github.com/shoriminimoe/pocket-pets/commit/c0ac4bf1257b298bd4b74302ee5068178557ad44))
* enlarge drag preview and lift it above the finger ([#16](https://github.com/shoriminimoe/pocket-pets/issues/16)) ([f9dad6d](https://github.com/shoriminimoe/pocket-pets/commit/f9dad6debabd15c6f7b02cf65b36f857a9c4a140))


### Bug Fixes

* bowl drag uses same fallback chain as rendered bowl ([3edcd13](https://github.com/shoriminimoe/pocket-pets/commit/3edcd13ead18a7598f1e6f0733b80d6646d66c02))
* gate decor render on bottomReservedDp to prevent first-frame flash ([046af2a](https://github.com/shoriminimoe/pocket-pets/commit/046af2a9afb9b323b2c0931b016205610799477c))
* inventory tools pick up immediately on touch-down ([a9072a8](https://github.com/shoriminimoe/pocket-pets/commit/a9072a87fc916dd73690bebb8fb043d4c9a6ecfe))
* inventory tools pick up immediately on touch-down ([a9e714f](https://github.com/shoriminimoe/pocket-pets/commit/a9e714f3767f61065a51ad2ac9601b6e15e38521))
* keep cat sprite fully inside the play area ([#11](https://github.com/shoriminimoe/pocket-pets/issues/11)) ([e6637eb](https://github.com/shoriminimoe/pocket-pets/commit/e6637eb8573598631d28e81fc38703fe845c6bac))
* keep cat sprite fully inside the play area ([#11](https://github.com/shoriminimoe/pocket-pets/issues/11)) ([60cb835](https://github.com/shoriminimoe/pocket-pets/commit/60cb835acb821633418c7c1fa9484e5dd0136e6c))
* PetScreen anchors decor + drops to measured tray height ([#12](https://github.com/shoriminimoe/pocket-pets/issues/12)) ([5ea105c](https://github.com/shoriminimoe/pocket-pets/commit/5ea105cf0f03e61debfe3e5c4529e4859b7d0ae8))

## [0.3.0](https://github.com/shoriminimoe/pocket-pets/compare/v0.2.0...v0.3.0) (2026-05-10)


### Features

* add cat-sprite fetch script with CC0-first candidate list ([1efe40f](https://github.com/shoriminimoe/pocket-pets/commit/1efe40f51a5bbd4881ede9d16a934f72dee7f098))
* add CatBehavior state machine in domain/behavior ([2ba11c9](https://github.com/shoriminimoe/pocket-pets/commit/2ba11c9dde49b09b3bb5a00c1cc37bf75b6772b2))
* add DragController state holder for in-flight drags ([71df6bd](https://github.com/shoriminimoe/pocket-pets/commit/71df6bdbd7f1fc94af34fb29b83818a5fce91273))
* add Eating and Playing cat states with sprite mappings ([fe147c2](https://github.com/shoriminimoe/pocket-pets/commit/fe147c2f85a4d413b66648b42bfb180d02198eec))
* add HabitatWorld value type for bowl-filled and toy state ([6eb6ec8](https://github.com/shoriminimoe/pocket-pets/commit/6eb6ec803ccda1b17ae813d59e6207accf4eb004))
* add Item enum, DropTarget, and pure dropTargetAt resolver ([e20e7f4](https://github.com/shoriminimoe/pocket-pets/commit/e20e7f4bf5ae1b7815af3d940e4c575f90b19e80))
* add layout-only InventoryTray Composable (gestures wired in PetScreen) ([24297fd](https://github.com/shoriminimoe/pocket-pets/commit/24297fdf39aa48e8d187661bb5967125ab2d65fb))
* add LPC cat candidate to fetch_cat_sprites.py with repack stub ([c7a3639](https://github.com/shoriminimoe/pocket-pets/commit/c7a3639b40cd1555909557c8e0cee6af9a173f50))
* add PetViewModel callbacks for food/scoop/toy/long-press ([800d458](https://github.com/shoriminimoe/pocket-pets/commit/800d458496d4d602d9979bea46496120c1283960))
* add procedural drawables for food, scoop, toy, bowl_full ([2ce1bdb](https://github.com/shoriminimoe/pocket-pets/commit/2ce1bdb930b4c17d857e2137cd5afb14231f0baf))
* bundle cat sprite (Cat 32x32 by GrafxKid, CC0) ([f7b4c74](https://github.com/shoriminimoe/pocket-pets/commit/f7b4c740c7fefd96dc1545341d3d0029346b7b98))
* bundle Surt's cat (CC0) repacked + add sprite primitives ([7390271](https://github.com/shoriminimoe/pocket-pets/commit/7390271b989f76c2265184f0d3e8c9f1d0286931))
* cat walks around — CatAnimations.forState + ViewModel ticker + PetScreen offset ([34f4f71](https://github.com/shoriminimoe/pocket-pets/commit/34f4f716bb32c5050cacbee95c53b8cb9b60351e))
* dispatch repo.feed/pet and clear bowl/toy on cat state transitions ([33d255b](https://github.com/shoriminimoe/pocket-pets/commit/33d255b3ec3574bcf09eb2ebf75dcbc5b3b28054))
* Eating and Playing exit to Idle when stateUntil is reached ([5cabd2a](https://github.com/shoriminimoe/pocket-pets/commit/5cabd2a367e7b301aa4c00df1fa9e4110abecaaf))
* Eating state on arrival at filled bowl with 5s stateUntil ([7cf73ac](https://github.com/shoriminimoe/pocket-pets/commit/7cf73acac9ccc3a7b95a85b6578ce2fde626f7ca))
* hungry cat only routes to bowl when filled (no bowl camping) ([079c56f](https://github.com/shoriminimoe/pocket-pets/commit/079c56ffa4ca6e704bab6e5fe0e168551b540171))
* PetScreen replaces button row with inventory tray + drag-drop layer ([1fb7c0d](https://github.com/shoriminimoe/pocket-pets/commit/1fb7c0dc6d533deec0c5e1de706f49c1affae9b1))
* Playing state on arrival at toy with 10s stateUntil ([5813506](https://github.com/shoriminimoe/pocket-pets/commit/5813506709709e3d1ce76e1fcf4f056996dd0275))
* swap cat art to Surt's pixel-art cat, add new sprite renderer ([f6f5841](https://github.com/shoriminimoe/pocket-pets/commit/f6f58414df1fba5621b5025838b50bd5e408d9c0))
* swap cat.png to 256x384 LPC-canonical sheet (walk + sit + lay) ([cfb8540](https://github.com/shoriminimoe/pocket-pets/commit/cfb85409aa06a065db05256da12d8702b02bca97))
* thread HabitatWorld through PetViewModel state and frame ticker ([812b1b3](https://github.com/shoriminimoe/pocket-pets/commit/812b1b36dea28aebf26a0c0c132760775318fede))
* thrown toy preempts target, except SLEEPY still routes to bed ([2f2fed7](https://github.com/shoriminimoe/pocket-pets/commit/2f2fed76b0845c49577e9eff2fd2a59ff9d2688d))


### Bug Fixes

* address review feedback on PR [#3](https://github.com/shoriminimoe/pocket-pets/issues/3) ([83949e2](https://github.com/shoriminimoe/pocket-pets/commit/83949e215f313e7cd8ee1992646dc4747c75fcf4))
* coerce non-walking facing to SOUTH so sit/lay don't read past sheet bounds ([5434294](https://github.com/shoriminimoe/pocket-pets/commit/54342940bc7ca890f3bf271d48e0911a0634879e))
* drag coordinate space, bowl drop rect, and drag-icon initial position ([615b1ef](https://github.com/shoriminimoe/pocket-pets/commit/615b1ef510f3778cac2b8693bdc8f3c7d75b2a1c))
* poop hit-rect math matches rendered position via clean offset modifier ([fb9edc0](https://github.com/shoriminimoe/pocket-pets/commit/fb9edc031fb2556d17ff56f14b027b06020c2c38))

## [0.2.0](https://github.com/shoriminimoe/pocket-pets/compare/v0.1.0...v0.2.0) (2026-05-09)


### Features

* add domain layer (Pet, Stats, GrowthStage, Mood, Speech) ([bce5006](https://github.com/shoriminimoe/pocket-pets/commit/bce50063409326d9a9d353dc0f8fc871e9546375))
* add Room DB, PetRepository, SettingsDataStore, DI container ([5b67e51](https://github.com/shoriminimoe/pocket-pets/commit/5b67e5172a3629482e2977fd3da00b6113d7831b))
* nav, adopt/pet/select/settings screens wired end-to-end ([b4ab96a](https://github.com/shoriminimoe/pocket-pets/commit/b4ab96aabe6e436760fff0099c2b7d28f3bf8fe5))
* notification permission, deep link, and idle chatter ([0a8e583](https://github.com/shoriminimoe/pocket-pets/commit/0a8e58375ba3642223fc1b114e609f3db629c02a))
* NotificationHelper, PetCareWorker, WorkManager scheduling ([681b063](https://github.com/shoriminimoe/pocket-pets/commit/681b063a594dfdc49c9d896aa8ba4e93ff779400))
* sprites, UI components, and PetViewModel ([2e86ab7](https://github.com/shoriminimoe/pocket-pets/commit/2e86ab7bc18ad1534bfbaf7ddbce28d595847ab2))
