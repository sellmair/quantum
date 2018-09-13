# Quantum 
State management library for Android

![GitHub top language](https://img.shields.io/github/languages/top/sellmair/quantum.svg)
[![Build Status](https://travis-ci.org/sellmair/quantum.svg?branch=develop)](https://travis-ci.org/sellmair/quantum)
![Bintray](https://img.shields.io/bintray/v/sellmair/sellmair/quantum.svg)


## Usage

##### gradle
```groovy

dependencies { 
    implementation "io.sellmair:quantum:0.2.0"
    
    // optional rx extensions
    implementation "io.sellmair:quantum-rx:0.2.0"
    
    // optional LiveData extensions
    implementation "io.sellmair:quantum-livedata:0.2.0"
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
Reducers will always be called by the internal thread of the quantum. 
Only one reducer will run at a time!

###### Example (simple reducer): 
A simple reducer that that says hallo to a certain user. 

```kotlin
class SimpleState(val name: String, val message: String = "" )

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
    // Don not try to load the content while currently loading
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
Listeners are always invoked by the main thread of your application!

###### Example: Without Extensions, Rare

```kotlin
quantum.addListener { state -> print(state.message) }
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
quantum.quit()
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
    Quantum<LoginState> by Quantum.create(LoginStat()) {
   
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