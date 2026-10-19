package br.com.smarttarget.karaoke.studio.data.model

enum class PersonRole {
  CANTOR,
  COMPOSITOR
}

data class Person(
  val name: String,
  val role: PersonRole
)

data class MusicMetadata(
  val guid: String,                      // GUID formatado em minúsculas e sem hifens
  val songTitle: String,                 // Nome da música solicitado
  val genre: String,                     // Gênero musical
  val persons: List<Person> = emptyList(), // Cantores e Compositores
  val album: String? = null,             // Solicitado se houverem cantores
  val workTitle: String? = null,         // Solicitado se houverem compositores (obra)
  val recordingDate: String? = null,     // Se só houverem cantores
  val compositionDate: String? = null,   // Se houverem compositores
  val classicalEra: String? = null,      // Para música clássica: Barroca, Clássica, Romântica, Moderna, Contemporânea
  val workType: String? = null           // Para música clássica: Ópera, Cantata, Ballet, Recital, Concerto, Sonata, Sinfonia, etc.
) {
  val singers: List<Person> get() = persons.filter { it.role == PersonRole.CANTOR }
  val composers: List<Person> get() = persons.filter { it.role == PersonRole.COMPOSITOR }
  val hasSingers: Boolean get() = singers.isNotEmpty()
  val hasComposers: Boolean get() = composers.isNotEmpty()
  val isClassical: Boolean get() = genre.equals("Música Clássica", ignoreCase = true) || genre.equals("Clássica", ignoreCase = true) || genre.equals("Classical", ignoreCase = true)
}
