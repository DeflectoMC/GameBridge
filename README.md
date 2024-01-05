# GameBridge

### Free Minecraft server hosting for people who know how to install mods and run servers at home

If you don't know how to do either of those things, then you can pay a Minecraft server hosting website.

Update:

I've simplified the code, and now it is possible in a much smaller file size with less dependencies added.

I will be working on the new recoded version soon.

The current version uses Ice4J and a relay server that I run to generate permanent /join IDs.

When you add the jar to your plugins folder, you get a server ID.

Then, players get to join by typing /gjoin <your server id>!


Unfortunately, because this uses WebRTC (which relies on STUN), it does not work on 100% of wifi networks, hence why I am recoding a version without STUN or WebRTC. For that, check out the repository for [Multiplayer-Possible])(https://github.com/DeflectoMC/Multiplayer-Possible/).

If you're curious, you can also check out my previous project written in Node.js, called [friendjoin](https://github.com/DeflectoMC/friendjoin/).

Note that there are some pretty major differences between these three projects, and how security, network etc. is handled will be different depending on which project you are talking about.


