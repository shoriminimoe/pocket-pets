# Changelog

## [0.3.0](https://github.com/shoriminimoe/pocket-pets/compare/v0.2.0...v0.3.0) (2026-05-10)


### Features

* add cat-sprite fetch script with CC0-first candidate list ([1efe40f](https://github.com/shoriminimoe/pocket-pets/commit/1efe40f51a5bbd4881ede9d16a934f72dee7f098))
* add CatBehavior state machine in domain/behavior ([2ba11c9](https://github.com/shoriminimoe/pocket-pets/commit/2ba11c9dde49b09b3bb5a00c1cc37bf75b6772b2))
* add LPC cat candidate to fetch_cat_sprites.py with repack stub ([c7a3639](https://github.com/shoriminimoe/pocket-pets/commit/c7a3639b40cd1555909557c8e0cee6af9a173f50))
* bundle cat sprite (Cat 32x32 by GrafxKid, CC0) ([f7b4c74](https://github.com/shoriminimoe/pocket-pets/commit/f7b4c740c7fefd96dc1545341d3d0029346b7b98))
* bundle Surt's cat (CC0) repacked + add sprite primitives ([7390271](https://github.com/shoriminimoe/pocket-pets/commit/7390271b989f76c2265184f0d3e8c9f1d0286931))
* cat walks around — CatAnimations.forState + ViewModel ticker + PetScreen offset ([34f4f71](https://github.com/shoriminimoe/pocket-pets/commit/34f4f716bb32c5050cacbee95c53b8cb9b60351e))
* swap cat art to Surt's pixel-art cat, add new sprite renderer ([f6f5841](https://github.com/shoriminimoe/pocket-pets/commit/f6f58414df1fba5621b5025838b50bd5e408d9c0))
* swap cat.png to 256x384 LPC-canonical sheet (walk + sit + lay) ([cfb8540](https://github.com/shoriminimoe/pocket-pets/commit/cfb85409aa06a065db05256da12d8702b02bca97))


### Bug Fixes

* address review feedback on PR [#3](https://github.com/shoriminimoe/pocket-pets/issues/3) ([83949e2](https://github.com/shoriminimoe/pocket-pets/commit/83949e215f313e7cd8ee1992646dc4747c75fcf4))
* coerce non-walking facing to SOUTH so sit/lay don't read past sheet bounds ([5434294](https://github.com/shoriminimoe/pocket-pets/commit/54342940bc7ca890f3bf271d48e0911a0634879e))

## [0.2.0](https://github.com/shoriminimoe/pocket-pets/compare/v0.1.0...v0.2.0) (2026-05-09)


### Features

* add domain layer (Pet, Stats, GrowthStage, Mood, Speech) ([bce5006](https://github.com/shoriminimoe/pocket-pets/commit/bce50063409326d9a9d353dc0f8fc871e9546375))
* add Room DB, PetRepository, SettingsDataStore, DI container ([5b67e51](https://github.com/shoriminimoe/pocket-pets/commit/5b67e5172a3629482e2977fd3da00b6113d7831b))
* nav, adopt/pet/select/settings screens wired end-to-end ([b4ab96a](https://github.com/shoriminimoe/pocket-pets/commit/b4ab96aabe6e436760fff0099c2b7d28f3bf8fe5))
* notification permission, deep link, and idle chatter ([0a8e583](https://github.com/shoriminimoe/pocket-pets/commit/0a8e58375ba3642223fc1b114e609f3db629c02a))
* NotificationHelper, PetCareWorker, WorkManager scheduling ([681b063](https://github.com/shoriminimoe/pocket-pets/commit/681b063a594dfdc49c9d896aa8ba4e93ff779400))
* sprites, UI components, and PetViewModel ([2e86ab7](https://github.com/shoriminimoe/pocket-pets/commit/2e86ab7bc18ad1534bfbaf7ddbce28d595847ab2))
