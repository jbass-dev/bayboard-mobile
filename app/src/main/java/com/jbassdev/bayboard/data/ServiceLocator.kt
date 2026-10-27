package com.jbassdev.bayboard.data

/** Tiny hand-rolled DI: one instance of each repository for the app. */
object ServiceLocator {
    val authRepository: AuthRepository by lazy { AuthRepository() }
    val boardRepository: BayBoardRepository by lazy { BayBoardRepository() }
}
