# reax synth 

Demo app wrapping [AudioKit](https://github.com/AudioKit/AudioKit) for a simple synth

It uses the basic "hello world" oscillator of AudioKit, and has a slider for duration and frequency!

It is built using:

* [integrant](https://github.com/weavejester/integrant) for data-driven architecture
* [rehook](https://github.com/wavejumper/rehook) for state management
* [rehook-dom](https://github.com/wavejumper/rehook-dom) to bind rehook and integrant together
* [reax](https://github.com/wavejumper/reax) for event-driven Swift modules


![image](https://i.imgur.com/EDZL9C4.png)


# Running:

```
git clone git@github.com:wavejumper/reax.git
cd reax/examples/synth
npm install -g shadow-cljs
npm install -g react-native-cli
npm install

# In one tab run:
shadow-cljs watch app

# In another tab after shadow-cljs compilation has finished:
react-native run ios
```
