#!/bin/zsh
# Capture the six Play Store screens in one language from the demo account:
#   scripts/store-assets/capture.sh <lang>        # en, de, es, fr, pt, it, nl, ru, ja, zh, ko, hi
# Screens land in scripts/store-assets/captures/<lang>/ (git-ignored); render.py turns them into store images.
#
# Before running:
# - Use the Pixel 10 Pro emulator (1280x2856). The taps below are its screen positions,
#   because button labels change with the language.
# - Install a debug build signed in to the demo account, with the "Demo account" banner hidden:
#   in core/ui/.../Header.kt change `if (m?.demo == true)` to `if (false && m?.demo == true)`,
#   build, install, then revert. Never use a real account. If the emulator can't reach the app's
#   server, also hide Recurring's "Showing what this phone saved" line the same way
#   (`if (loaded.offline)` in feature/recurring/.../RecurringList.kt).
# - Set AGENT_DEVICE to the agent-device command bound to the emulator, and SERIAL to its adb serial.
# - Clean status bar: adb shell settings put global sysui_demo_allowed 1, then the systemui demo
#   broadcasts (enter; clock 0941; battery 100; wifi 4, mobile hidden; notifications hidden).
set -e
A=${AGENT_DEVICE:-agent-device}
SERIAL=${SERIAL:-emulator-5554}
L=$1; O=${0:A:h}/captures/$L; mkdir -p $O
p(){ $A press $1 $2 >/dev/null; }
shot(){ sleep ${2:-2}; $A screenshot $O/$1.png >/dev/null; }
open_app(){ $A open app.getkosha --activity app.getkosha/.MainActivity --relaunch >/dev/null; }

adb -s $SERIAL shell cmd locale set-app-locales app.getkosha --locales $L
$A settings appearance light >/dev/null; open_app; shot overview 6
p 396 2686; shot activity 4      # Activity tab
p 887 2686; shot recurring 6     # Recurring tab
p 1133 2686; shot accounts 4     # Accounts tab
p 641 2686; sleep 2              # Add: type 24.07 + 5.5 on the keypad
for k in "489 1896" "186 2082" "186 2453" "489 2453" "186 2268" "1101 2457" "489 2082" "186 2453" "489 2082"; do p ${=k}; sleep 0.3; done
p 640 1697; sleep 1; $A type Lidl >/dev/null; sleep 4; p 429 2372; shot add 2   # payee, then its suggestion
$A settings appearance dark >/dev/null; open_app; shot overview-dark 6
$A settings appearance light >/dev/null
adb -s $SERIAL shell cmd locale set-app-locales app.getkosha --locales ""
