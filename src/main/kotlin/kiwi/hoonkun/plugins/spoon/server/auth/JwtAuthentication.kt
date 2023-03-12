package kiwi.hoonkun.plugins.spoon.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
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
                    .withIssuer(configurations.host)
                    .build()
            )
            validate { credential ->
                if (credential.payload.claims.containsKey("username")) JWTPrincipal(credential.payload) else null
            }
        }
    }
}

suspend fun PipelineContext<Unit, ApplicationCall>.generateJWT(configurations: SpoonConfiguration) {
    val user = call.receive<User>()
    // TODO: add user database compare here
    val token = JWT.create()
        .withClaim("username", user.username)
        .withIssuer(configurations.host)
        .withExpiresAt(Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24))
        .sign(Algorithm.HMAC256(configurations.secret))
    call.respond(hashMapOf("token" to token))
}
