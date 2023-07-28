package me.rhunk.snapmapper.impl

import me.rhunk.snapmapper.AbstractClassMapper
import me.rhunk.snapmapper.MapperContext
import me.rhunk.snapmapper.ext.findConstString
import me.rhunk.snapmapper.ext.getStaticConstructor
import me.rhunk.snapmapper.ext.isEnum

class StoryBoostStateMapper : AbstractClassMapper() {
    override fun run(context: MapperContext) {
        for (clazz in context.classes) {
            val firstConstructor = clazz.directMethods.firstOrNull { it.name == "<init>" } ?: continue
            if (firstConstructor.parameters.size != 3) continue
            if (firstConstructor.parameterTypes[1] != "J" || firstConstructor.parameterTypes[2] != "J") continue

            val storyBoostEnumClass = context.getClass(firstConstructor.parameterTypes[0]) ?: continue
            if (!storyBoostEnumClass.isEnum()) continue
            if (storyBoostEnumClass.getStaticConstructor()?.implementation?.findConstString("NeedSubscriptionCannotSubscribe") != true) continue

            context.addMapping("StoryBoostStateClass", clazz.type.replace("L", "").replace(";", ""))
        }
    }
}