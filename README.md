# Reax

> A quote goes here

Event driven RPC between a Swift backend and a Clojurescript front end. Influenced by the Xi-editor.

**Note 1:** API is subject to change as I experiment implementing it within my own application.

**Note 2:** also a WIP. I am writing the documentation before I finish publishing the code :p

# Prior reading

Here are some resources to help familiarise yourself with Native Modules for React Native, and the Xi-editor architecture:

* [Native Modules](https://facebook.github.io/react-native/docs/native-modules-ios)
* [Swift in React Native - The Ultimate Guide Part 1: Modules](https://teabreak.e-spres-oh.com/swift-in-react-native-the-ultimate-guide-part-1-modules-9bb8d054db03)
* [Xi editor](https://xi-editor.io/)

# Design goals

Following Xi's [design decisions](https://github.com/xi-editor/xi-editor#design-decisions):

* **Separation into front-end and back-end modules**
* **Native UI** 
* **Asynchronous operations** 
* **Functional core, imperative shell** 
* **Programming in data** 

The front end is rendering a UI derived from the events (facts) it has received. The front end should never block, and is modelled to be eventually consistent.

* This design plays to Clojure's strengths greatly, where data comes first!
* This design makes the limitations of the "single-threaded event loop" model of the JS runtime a bit easier to accept.
* This architecture leverages a reasonably expressive back end language (Swift) for its "imperative shell"

# Getting started

## Requirements

This project assumes you have a React Native project targetting `ios` and a tool for building Clojurescript assets (eg [shadow-cljs](https://github.com/thheller/shadow-cljs)).

Your project needs to be able to support native modules. If you are using Expo, you will need to [eject to ExpoKit](https://docs.expo.io/versions/latest/expokit/eject/).

Please check this [example](#) project for reference.

Reax ships with no transitive dependencies. The front end optionally depends on [integrant](https://github.com/weavejester/integrant), though you can use any library for managing app state.

### Clojurescript

Add this dependency to your projects `deps.edn` file:

```clojure
{}
```

### Swift

Add this dependency to your projects `ios/Podfile` file:

```ruby
pod 'RNReax', :git => 'https://github.com/wavejumper/RNReax', :tag => '1.0.0'
```

# Using the library

## Events

A Reax event looks like this: 

```clojure
["getUser" {:userId "yoko"}]
```

A Reax event can be dispatched from either Clojurescript or Swift, and are considered asynchronous.

Generally a dispatch event will originate from the front end (eg via user interaction) and the Reax Swift module will asynchronously perform the operation.

It is up to the end-user to define the semantics of the operation (eg if the event is idempotent). Reax simply provides the glue.

During transport, events are represented as JSON (conveniently, thanks to the [codable](https://developer.apple.com/documentation/swift/codable) protocols in Swift, and `js/JSON` in JS). Ideally, a [transit-json](https://github.com/cognitect/transit-format) codable impl would be nicer for richer types.

## Event router

The Swift type system defines the schema of available events and valid arguments an event has:

Anything `Decodable` is a valid event id, so long as it also implements the `ReaxEventRouter` protocol. An event id is generally represented as an enum encoded as a string.

```swift
enum MyEventRouter: String, Codable, ReaxEventRouter {
  typealias Context = MyContext
  typealias Result = MyResult

  case getUser

  func routeEvent() -> ((_ ctx: Context, _ from: Data) -> Either<Result, ReaxError>) {
    switch self {
    case .getUser:
      return mutationFactory(GetUserHandler.self)
    }
  }
}
```

In this example we have defined a single event, `getUser`, which maps to the `GetUserHandler` (defined below). 

The signature for `routeEvent` returns a closure, which then returns the result.

The `mutationFactory` fn is a helper function that returns a closure that decodes, and invokes the event handler. This fn also neatly handles any deserialization errors.
This makes makes the router incredibly succicent for most use cases. The router should only care about matching event id's to event handlers!

## Event handlers

Event handlers are represented with the `ReaxEventHandler` protocol. Similar to event ids, anything that implements the `Decodable` protocol is a valid handler. A handler is generally represented as a struct.

```swift
struct GetUserHandler: Codable, ReaxMutation {
  typealias Context = MyContext
  typealias Result = MyResult

  var userId: String

  func invoke(ctx: Context) -> Either<Result, ReaxError> {
    return Either.Left(ctx.pool.getUser(userId))
  }
}
```

## Context and results

These are two `typealias` arguments both `ReaxEventRouter` and `ReaxEventHandler` must provide to allow for customised context and results.

### Context

The `Context` typealias allows the end user to define required components  (eg, a database pool or some custom state) that will get passed to the handler's `invoke` method.

`Context` can be any data structure implementing the `ReaxContext` protocol. They are generally structs.

```swift
struct MyContext: ReaxContext {

  var pool: Database
  var channel:  ((_ ctx: Context, _ from: Data) -> Either<MyResult, ReaxError>)

  func state() -> ReaxContextState {
    return ReaxContextState.Started
  }
}
```

### Result

The `Result` typealias is the value the Reax module sends to the front end. It can be represented as anything `Encodable`, and is generally a struct, or enum.

```swift 
struct MyResult {
  var name: String
  var userId: String
}
```

## Reax errors

In the case of handling errors, the `ReaxError` enum is returned to the user.

## ReaxEventEmitter

Finally, the `ReaxEventEmitter` class is what the front end interacts with. This class is a subclass of `RCTEventEmitter`. 

A skeloton Reax class looks like this:

```swift 
@objc(Midi)
class Midi: ReaxEventEmitter {
  var ctx = MidiContext()
  
  @objc
  func start() {
    logger.info("Starting MIDI")
    let deviceManager = DeviceManager()
    let observations = deviceManager.initSubscriptions()
    self.ctx = MidiContext(deviceManager: deviceManager, observations: observations)
  }
  
  @objc
  func stop() {
    // TODO: implement shutdown hooks for compoents
    self.ctx = MidiContext()
  }
  
  @objc(dispatch:args:)
  func dispatch(id: NSString, args: NSString) {
    self.invoke(MyEventRouter.self, ctx: self.ctx, id: id as String, args: args as String)
  }
}
```

### dispatch

This method gets called when the front end sends an event to the module. There is a public `invoke` method on the `ReaxEventEmitter` class which neatly handles all deserialization, serialization and responding. 

### Other helper methods

The `ReaxEventEmitter` class contains one useful method, `channelFactory` for constructing 'channels' out of the modules concrete `Result` type.

This can be used for dispatching results to the front end asynchronously, eg some event triggered by [Key-Value Observing](https://nshipster.com/key-value-observing/)

### Boilerplate

A `<ModuleName.m>` file will also have to be created, exposing the module to the front end:

```objc
#import "React/RCTBridgeModule.h"
#import "React/RCTEventEmitter.h"

@interface RCT_EXTERN_MODULE(Midi, RCTEventEmitter)

RCT_EXTERN_METHOD(start)
RCT_EXTERN_METHOD(stop)
RCT_EXTERN_METHOD(dispatch:(NSString *)id args:(NSString *)args)

@end
```

## Reax lifecycle

All `ReaxEventEmitter` classes have to implement `start` and `stop` methods, which will be invoked from the front end when the user initializes the Reax module.

These two methods are how you manage the lifecycle of stateful Reax modules, eg setting up `Context`.

This pattern means that both configuration and lifecycle management should come from a centralized location: your front-end (eg, via [integrant](https://github.com/weavejester/integrant)).

If you need more custom dependency injection, you can always implement the [RCTBridgeDelegate protocol](https://facebook.github.io/react-native/docs/native-modules-ios#dependency-injection)

## Clojurescript 

### Integrant

The `reax.integrant` namespace provides [integrant](https://github.com/weavejester/integrant) integration via the `:reax/module` key.

Within your integrant config, you can define a Reax module like so:

```clojure
(ns example
  (:require [integrant.core :as ig]
            [example.link :as link]
            [reax.integrant])) ;; <-- require for :reax/module key

(defmethod ig/init-key :app/db [_ init-value]
  (atom init-value))

(defn config []
  {:app/db {}
   :link/handler {:db      (ig/ref :app/db)
                  :handler link/handler} ;; <--- implementation explained in next section
   [:reax/module :reax/link] {:class-name     "Link"
                              :event-listener (ig/ref :link/handler}})
```

It accepts three keys:

* `:class-name`: the name of the Reax class
* `:result-handler`: a function that handles all Reax results
* `:error-handler`: a function that handles all Reax errors

The returned value from the integrant component will be a map containing a `:dispatch` key. 

This is how you send events to your Reax module:

```clojure
(let [{:keys [dispatch]} reax-module]
  (dispatch "getUser" {:userId "Yoko"}))
```

All serialization will be taken care of!

### Result handlers

Generally you will want to react to incoming events (eg, by mutating some app state).

A good pattern is to implement an event handler `init-key` that has a dependency on your `:app/db`, or whatever other context it requires:

```clojure
(ns example.link
  (:require [integrant.core :as ig]))

(defn handler [db event]
  (assoc db :my-event event))

(defmethod ig/init-key :app/handler [_ {:keys [db handler]}]
  (fn [event]
    (swap! db handler event)))
```

# Notes / considerations

* All Reax modules are singletons, because all Native Modules are singletons. 

# TODOs / Design questions

## Allow for custom arguments to be passed into the start method

For neater dependency injection, it would be nice if the `start` method had a way to pass in generic args to the component...

## Javascript front end

There is nothing in this codebase that ties it to just Clojurescript. Look to the [:npm-module](https://shadow-cljs.github.io/docs/UsersGuide.html#target-npm-module) target in shadow-cljs and offer NPM package.

## Unit tests

They would be good :)

## Remove the boilerplate of the Objective-C declaration

If there is an easy way to template/automatically define the boilerplate-y `<ModuleName.m>`  `RCT_EXTERN_MODULE`  code that would be nice.

## Remove the idea of the result type

The result type was added as a way to avoid having the `invoke` operation return meaningless voids everywhere. 

To better represent the async nature of Reax, and if all mutations are generally something side-effectful, perhaps something like [bow effects](https://bow-swift.io/docs/effects/overview/) would be a good idea for richer types.

## Define a better way for custom user errors

The `ReaxError` enum isn't really extensible, and without [higher kinded types](https://www.stephanboyer.com/post/115/higher-rank-and-higher-kinded-types), I'm not really sure of the best way to allow for better extensibility.

## RCTConvert

JSON transport makes for a convenient, and type safe API thanks to codable. [RCTConvert](https://github.com/facebook/react-native/blob/master/React/Base/RCTConvert.h) allows for helper functions all accept a JSON value as input and map it to a native Objective-C type or class. 

Investigate this as an alternative for performance reasons.

