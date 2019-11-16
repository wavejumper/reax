import Foundation
import RNReax
import AudioKit

struct SynthResult: Codable {
  var started: Bool
}

struct SynthContext: ReaxContext {
  var oscillator: AKOscillator
  
  func state() -> ReaxContextState {
    return ReaxContextState.started
  }
  
  func status() -> SynthResult {
    let started = oscillator.isStarted
    let result = SynthResult(started: started)
    return result
  }
}

struct SetFrequncyHandler: Codable, ReaxHandler {
  typealias Context = SynthContext
  typealias Result = SynthResult

  var frequency: Double

  func invoke(ctx: Context) -> Either<Result, ReaxError> {
    ctx.oscillator.frequency = frequency
    return Either.left(ctx.status())
  }
}

struct StartSynthHandler: Codable, ReaxHandler {
  typealias Context = SynthContext
  typealias Result = SynthResult

  func invoke(ctx: Context) -> Either<Result, ReaxError> {
    ctx.oscillator.start()
    return Either.left(ctx.status())
  }
}

struct StopSynthHandler: Codable, ReaxHandler {
  typealias Context = SynthContext
  typealias Result = SynthResult

  func invoke(ctx: Context) -> Either<Result, ReaxError> {
    ctx.oscillator.stop()
    return Either.left(ctx.status())
  }
}

enum SynthRouter: String, Codable, ReaxRouter {
  typealias Context = SynthContext
  typealias Result = SynthResult

  case setFrequency
  case startSynth
  case stopSynth

  func routeEvent() -> ((_ ctx: Context, _ from: Data) -> Either<Result, ReaxError>) {
    switch self {
    case .setFrequency:
      return eventHandler(SetFrequncyHandler.self)
    case .stopSynth:
      return eventHandler(StopSynthHandler.self)
    case .startSynth:
      return eventHandler(StartSynthHandler.self)
    }
  }
}

@objc(Synth)
class Synth: ReaxEventEmitter {
  var ctx = SynthContext(oscillator: AKOscillator())
  
  override func supportedEvents() -> [String]! {
    return [self.errorType(), self.resultType()]
  }
  
  @objc
  func start() {
    print("Starting AuidoKit...")
    AudioKit.output = self.ctx.oscillator
    do {
      try AudioKit.start()
    } catch {
      print("Failed to start AudioKit")
    }
  }
  
  @objc
  func stop() {
    print("Stopping AudioKit...")
    do {
      try AudioKit.stop()
    } catch {
      print("Failed to stop AudioKit")
    }
  }
  
  @objc(dispatch:args:)
  func dispatch(id: NSString, args: NSString) {
    self.invoke(SynthRouter.self, ctx: self.ctx, id: id as String, args: args as String)
  }
}
