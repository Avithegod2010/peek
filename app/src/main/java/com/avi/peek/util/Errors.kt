package com.avi.peek.util

import com.avi.peek.data.remote.GuestErrorKind
import com.avi.peek.data.remote.GuestException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun Throwable.userMessage(): String = when (this) {
    is GuestException -> when (kind) {
        GuestErrorKind.NETWORK -> "No connection to Instagram. Showing what's saved."
        GuestErrorKind.NOT_FOUND -> message ?: "That account doesn't exist."
        GuestErrorKind.RATE_LIMITED -> "Instagram asked us to slow down. Try again in a few minutes."
        GuestErrorKind.LOGIN_REQUIRED -> "Instagram wants a login for this content. Peek never asks for your password."
        GuestErrorKind.BLOCKED -> "Instagram blocked this request. Try again later."
        GuestErrorKind.PARSE -> "Instagram changed its page layout. Peek couldn't read this one."
        GuestErrorKind.SERVER -> "Instagram is having a moment. Try again soon."
        GuestErrorKind.UNKNOWN -> message ?: "Something went wrong."
    }
    is UnknownHostException -> "You appear to be offline."
    is SocketTimeoutException -> "The request timed out. Instagram can be slow."
    is IOException -> "Network hiccup. Check your connection."
    else -> message ?: "Something unexpected happened."
}
