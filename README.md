<p align="left">
  <img width="512" src="https://github.com/sellmair/quantum/blob/feature/1-executorservice-based-quantum/logo/medium.png?raw=true"><br>
</p>

## State management library for Android
![GitHub top language](https://img.shields.io/github/languages/top/sellmair/quantum.svg)
[![Build Status](https://travis-ci.org/sellmair/quantum.svg?branch=develop)](https://travis-ci.org/sellmair/quantum)
![Bintray](https://img.shields.io/bintray/v/sellmair/sellmair/quantum.svg)


## Usage

##### gradle
```groovy

dependencies { 
    implementation "io.sellmair:quantum:0.3.1"
    
    // optional rx extensions
    implementation "io.sellmair:quantum-rx:0.3.1"
    
    // optional LiveData extensions
    implementation "io.sellmair:quantum-livedata:0.3.1"
}
```


##### Define a State
States should always be immutable. I highly recommend using 
kotlin data classes to ensure immutability. 


Example:

```kotlin
data class MyState(
    val isLoading: Boolean = false, 
    val error: Error? = null,
    val content: Content? = null, 
    val userLocation: Location? = null)
```

##### Create a Quantum
A Quantum is the owner of your state. It applies all reducers, 
invokes actions and publishes new states.

Example:


```kotlin
// Create a new Quantum with initial state. 
val quantum = Quantum.create(MyState())
```


##### Enqueue a Reducer
Reducers are functions that take the current state and create a new state. 
Reducers will always be called by a internal thread of the quantum. 
Only one reducer will run at a time!

###### Example (simple reducer): 
A simple reducer that that says hallo to a certain user. 

```kotlin
data class SimpleState(val name: String, val message: String = "" )

val quantum = Quantum.create(SimpleState("Julian"))

fun sayHello() = quantum.setState {
    copy(messagge = "Hello $name")
}

```


###### Example (load content): 
Much more complicated reducer problem:  <br>
We want to 
- Load content from repository async
- Ensure that only one loading operation is running at a time
- Publish the content when fetched successfully
- Publish the error when an error occurred 

```kotlin
// Reducer that fetches the content (if not currently loading)
fun loadContent() = quantum.setState {
    // Do not try to load the content while currently loading
    // Returning this (the current / input state) signals the quantum that 
    // this reducer was a NOOP
    if(isLoading) return@setState this
    
    // Dispatch a async loading operation with myRepository (exemplary)
    myRepository.loadContent
        .onSuccess(::onContentLoaded)
        .onError(::onError)
        .execute()
        
    // Copy the current state but set loading flag  
    copy(isLoading = true)
    
}

fun onContentLoaded(content: Content) = setState {
    // Content loaded: 
    // Copy current state and clear any error
    copy(content = content, error = null)
}

fun onError(error: Error) = setState {
    // Copy current state but publish the error
    copy(error = error)
}
```

##### Listen for changes
Listeners are invoked by Android's main thread by default. 
It is possible to configure the thread which invokes listeners by specifying an Executor.

###### Example: Without Extensions, Rare

```kotlin
quantum.addStateListener { state -> print(state.message) }
```

###### Example: Without Extensions, Function

```kotlin
fun onState(state: SimpleState){
  // be awesome
}

fun onStart() {
    quantum.addListener(::onState)
}

fun onStop() {
    quantum.removeListener(::onState)
}
```


###### Example: Rx (recommended)

```kotlin
fun onStart() {
    quantum.rx.subscribe { state -> /* be awesome */ }
}
```


##### Debugging

###### History
It is possible to record all states created in a Quantum. 

```kotlin
val quantum = Quantum.create(MyState()).apply { 
    history.enabled = true
}

fun debug(){
   for(state in quantum.history){
       print(state)
   }
}
```


##### Quitting
A Quantum has to be stopped if no longer needed to stop the internal background 
thread and to release all resources 

```kotlin
quantum.quit() // will quit as fast as possible
quantum.quitSafely() // will quit after all currently enqueued reducers / actions
```


##### ViewModel (Suggestion)
I suggest having one 'ViewState' for each ViewModel. The ViewModel itself
might want to implement Quantum itself. 

###### Example:

```kotlin
data class LoginState(
    val email: String = "",
    val password: String = "", 
    val user: User? = null)

class LoginViewModel(private val loginService: LoginService): 
    ViewModel(), 
    Quantum<LoginState> by Quantum.create(LoginState()) {
   
   fun setEmail(email: String) = setState {
        copy(email = email)
   }                   
   
   fun setPassword(password: String) = setState {
        copy(password = password)
   }
   
   fun login() = setState {
       val user = loginService.login(email, password)
       copy(user = user)
   }
   
   override fun onCleared() {
        // Quit the quantum
        quit()
   }
}

```


#### Configuration
It is possible to configure the default configuration of Quantum for your whole application. 
For example: It is possible to specify the default threading mode, history settings 
or even the thread pool that is shared for multiple Quantum instances.

##### Global configuration
```kotlin
// configure defaults
Quantum.configure {
    // Quantum instances will use the given thread pool by default
    this.threading.default.mode = Threading.Pool
            
    // Listeners are now invoked by a new background thread
    this.threading.default.callbackExecutor = Executors.newSingleThreadExecutor()
            
    // Override the default shared thread pool
    this.threading.pool = Executors.newCachedThreadPool()
            
    // Set history default to enabled with limit of 100 states
    this.history.default.enabled = true
    this.history.default.limit = 100
            
    // Get info's from quantum
    this.logging.level = LogLevel.INFO
}
```

##### Instance configuration

```kotlin
 Quantum.create(
        // initial state
        initial = LoginState(), 
        
        // invoke listeners by background thread
        callbackExecutor = Executors.newSingleThreadExecutor(),
        
        // use thread pool 
        threading = Threading.Pool)
```

## Behind the scenes

<p align="center">
  <img src="https://github.com/sellmair/quantum/blob/illustration/etc/illustration.jpeg?raw=true"><br>
</p>