package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

/**
 * Modified from [org.apache.maven.plugins.shade.resource.ApacheLicenseResourceTransformerTest.java](https://github.com/apache/maven-shade-plugin/blob/master/src/test/java/org/apache/maven/plugins/shade/resource/ApacheLicenseResourceTransformerTest.java).
 */
class ApacheLicenseResourceTransformerTest : BaseTransformerTest<ApacheLicenseResourceTransformer>() {

  init {
    setupTurkishLocale()
  }

  @Test
  fun canTransformResource() {
    assertThat(transformer.canTransformResource("META-INF/LICENSE")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/LICENSE.TXT")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/License.txt")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/LICENSE.md")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/License.md")).isTrue()
    assertThat(transformer.canTransformResource("META-INF/MANIFEST.MF")).isFalse()
  }
}
