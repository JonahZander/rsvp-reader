# Add project specific ProGuard rules here.

# xmlpull's org.xmlpull.v1.XmlPullParser is excluded from the epub4j-core
# dependency in build.gradle.kts because Android's platform already provides
# it; kxml2's factory implementation classes referenced from
# META-INF/services are like wise not bundled, so R8 warns about them.
-dontwarn org.xmlpull.v1.**
-dontwarn org.xmlpull.mxp1.**
-dontwarn org.xmlpull.mxp1_serializer.**

# epub4j-core reflectively probes for optional dependencies (SLF4J, XML
# parser factories) that aren't present in this app; keep R8 from failing
# the build on the resulting warnings.
-dontwarn org.slf4j.**
-dontwarn nl.siegmann.epublib.**
