package kiwi.hoonkun.plugins.spoon.server.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import kiwi.hoonkun.plugins.spoon.Main
import kiwi.hoonkun.plugins.spoon.SpoonConfiguration
import kiwi.hoonkun.plugins.spoon.server.structures.User
import java.util.*

fun Application.jwtAuthentication(configurations: SpoonConfiguration) {
    authentication {
        jwt {
            realm = "access to minecraft server with full privilege."
            verifier(
                JWT
                    .require(Algorithm.HMAC256(configurations.secret))
                    .withIssuer(configurations.hostURL)
                    .build()
            )
            validate { credential ->
                if (credential.payload.claims.containsKey("username")) JWTPrincipal(credential.payload) else null
            }
        }
    }
}

suspend fun PipelineContext<Unit, ApplicationCall>.respondJWT(parent: Main) {
    val user = call.receive<User>()

    val username = user.username
    val rawPassword = user.password

    val matchingUser = parent.users.find {
        it.username == username && BCrypt.verifyer().verify(rawPassword.toCharArray(), it.password).verified
    }

    if (matchingUser == null) {
        call.respond(HttpStatusCode.Unauthorized, "Not Authorized")
        return
    }

    val token = JWT.create()
        .withClaim("username", user.username)
        .withIssuer(parent.configurations.hostURL)
        .withExpiresAt(Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24))
        .sign(Algorithm.HMAC256(parent.configurations.secret))
    call.respond(hashMapOf("token" to token))
}
