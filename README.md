# stash-gui

`stash-gui` is a graphical interface to [stash](https://github.com/rorokimdim/stash).

<img src="screenshots/fruits.png" width=350></img>

This is a work in progress. Any contribution is welcome.

## Installation

Linux and mac binaries are available at [releases](https://github.com/rorokimdim/stash-gui/releases).

## Development

The code is written in [clojurescript](https://clojurescript.org/) and uses [electron-js](https://www.electronjs.org/).
There are several tutorials on electron-js on youtube (for just javascript unfortunately);
try https://youtu.be/tqBi_Tou6wQ

### Install npm packages

```bash
npm install
```

### Build and start dev environemnt

```bash
npm run dev
```

or `./watch`

### Start app

```bash
npx electron .
```

or `./start`

### Build a mac-app

```bash
./build-mac-app
```

### Build a linux-app

```bash
./build-linux-app
```

## Credits

1. [stash](https://github.com/rorokimdim/stash)
2. [clojurescript](https://clojurescript.org/)
3. [electron](https://www.electronjs.org/)
4. [shadow-cljs](https://github.com/thheller/shadow-cljs)
5. [reagent](https://reagent-project.github.io/)
6. [re-frame](https://github.com/day8/re-frame)
7. All of these [libraries](https://github.com/rorokimdim/stash-gui/blob/master/package.json#L15) and all the things they depend on
8. All of these [libraries](https://github.com/rorokimdim/stash-gui/blob/master/shadow-cljs.edn#L4) and all the things they depend on
9. Every stash file is a [sqlite](https://sqlite.org/) file
