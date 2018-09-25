<p align="left">
  <img width="512" src="https://github.com/sellmair/quantum/blob/develop/etc/logo/big.png?raw=true"><br>
</p>

## State management library for Android
![GitHub top language](https://img.shields.io/github/languages/top/sellmair/quantum.svg)
[![Build Status](https://travis-ci.org/sellmair/quantum.svg?branch=develop)](https://travis-ci.org/sellmair/quantum)
![Bintray](https://img.shields.io/bintray/v/sellmair/sellmair/quantum.svg)


 ## What is it

 Quantum is a general purpose state management library designed for building easy, stable and thread safe
 Android applications. It was inspired by [AirBnb's MvRx](https://github.com/airbnb/MvRx) and tailored
 for building reliable ViewModels.
 
 <p align="center">
   <img src="https://github.com/sellmair/quantum/blob/develop/etc/illustration.jpeg?raw=true"><br>
 </p>


## Usage

##### gradle
```groovy

dependencies { 
    implementation "io.sellmair:quantum:1.0.0-beta.1"
    
    // optional rx extensions
    implementation "io.sellmair:quantum-rx:1.0.0-beta.1"
    
    // optional LiveData extensions
    implementation "io.sellmair:quantum-livedata:1.0.0-beta.1"
}
```


##### Define a State
States should always be immutable. I highly recommend using 
kotlin data classes to make immutability easy 👍 


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
Reducers will always be called by a internal thread of the `Quantum`. 
Only one reducer will run at a time!
Reducers are allowed to return the same (untouched) instance to signal a no-operation.

###### Example (simple reducer): 
A simple reducer that that says hello to a certain user. 

```kotlin
data class SimpleState(val name: String, val message: String = "" )

val quantum = Quantum.create(SimpleState("Julian"))

fun sayHello() = quantum.setState {
    copy(message = "Hello $name")
}

```

Unlike other "State Owner" concepts, Quantum allows reducers to dispatch async operations.
This decision was made to give developers the option to handle side-effects 
inside a safer environment. 


###### Example (load content): 
Much more complicated reducer problem:  <br>
We want to 
- Load content from repository asyncronously
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


##### Enqueue an Action
Actions are parts of your code that require the most recent state, but do not intend to change it. 
Actions will always be called by a internal thread of the `Quantum` and run after
all reducers are applied.


```kotlin
val quantum = Quantum.create(SimpleState(name = "Balazs"))

quantum.setState {
    copy(name = "Paul")
}

quantum.withState {
    // will print 'Hello Paul'
    Log.i("Readme", "Hello $name")
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


##### Nested Quantum / Map

It is possible to map a Quantum to create a 'Child-Quantum' which can enqueue reducers and actions
as usual. The state of this child will be in sync with the parent Quantum.

###### Example: Child

```kotlin
data class ChildState(val name: String, val age: Int)

data class ParentState(val name: String, val age: Int, val children: List<ChildState>)

// Get the quantum instance of the parent state
val parentQuantum: Quantum<ParentState> =  /* ... */

// Create the child state
val childQuantum = parentQuantum
    .map { parentState ->  parentState.children }
    .connect { parentState, children -> parentState.copy(children = children) }

// Increase the age of all children
childQuantum.setState { children ->
     children.map { child -> child.copy(age=child.age++) }
}
```

##### Debugging

###### History
It is possible to record all states created in a `Quantum`. 

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
A `Quantum` has to be stopped if it's no longer needed, in order to stop the internal background 
thread and release all resources.

```kotlin
quantum.quit() // will quit as fast as possible
quantum.quitSafely() // will quit after all currently enqueued reducers / actions
```


##### ViewModel (Suggestion)
I suggest having one 'ViewState' for each ViewModel. The ViewModel itself
might want to implement `Quantum` itself. 

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
It is possible to configure the defaults of Quantum for your whole application. 
For example: It is possible to specify the default threading mode, history settings, 
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

