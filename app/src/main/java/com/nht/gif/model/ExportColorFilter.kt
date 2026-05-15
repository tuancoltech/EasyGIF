package com.nht.gif.model

/** Color filter preset applied to frames before GIF/WebP encoding via FFmpeg `-vf` chain. */
enum class ExportColorFilter(val vfChain: String?) {
    NONE(vfChain = null),
    VINTAGE(vfChain = "curves=r='0/0 0.5/0.6 1/1':b='0/0 0.5/0.4 1/0.85',hue=s=0.7"),
    NEON(vfChain = "hue=s=2.5,eq=brightness=0.05:contrast=1.1"),
    NOIR(vfChain = "hue=s=0,eq=contrast=1.5"),
}
