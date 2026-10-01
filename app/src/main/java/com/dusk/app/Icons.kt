package com.dusk.app

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A Tabler outline icon (MIT, see /licenses) tinted like the text around it. */
@Composable
fun TIcon(
    res: Int,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
    desc: String? = null
) {
    Icon(painterResource(res), contentDescription = desc, modifier = modifier.size(size), tint = tint)
}

/** Body runs, mind thinks, food is a bowl, sleep is the moon, social is people. */
fun kindIcon(kind: String): Int = when (kind) {
    "body" -> R.drawable.ic_t_run
    "mind" -> R.drawable.ic_t_brain
    "food" -> R.drawable.ic_t_salad
    "sleep" -> R.drawable.ic_t_moon
    "social" -> R.drawable.ic_t_users
    else -> R.drawable.ic_t_circle_dot
}

fun substanceIcon(sub: String): Int = if (sub == FLOW_CIGARETTE) R.drawable.ic_t_smoking else R.drawable.ic_t_cannabis
