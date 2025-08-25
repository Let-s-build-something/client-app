package ui.dev.experiment

import androidx.lifecycle.viewModelScope
import data.io.experiment.ExperimentIO
import data.io.experiment.ExperimentSet
import data.io.experiment.ExperimentSetValue
import data.io.experiment.FullExperiment
import data.shared.SharedModel
import korlibs.datastructure.iterators.fastIterateRemove
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ui.dev.DeveloperConsoleDataManager

internal val experimentModule = module {
    factory { ExperimentRepository(get(), get()) }
    viewModelOf(::ExperimentModel)
}

class ExperimentModel(
    private val repository: ExperimentRepository,
    private val dataManager: DeveloperConsoleDataManager
): SharedModel() {
    private val _sets = MutableStateFlow(listOf<ExperimentSet>())

    val experiments = dataManager.experiments.asStateFlow()
    val activeExperiments = dataManager.activeExperiments.asStateFlow()
    val sets = _sets.asStateFlow()

    init {
        viewModelScope.launch {
            getSets()
            getExperiments()

            if (dataManager.experiments.value.isEmpty() && _sets.value.isEmpty()) {
                val newSet = ExperimentSet(
                    name = "Sample set",
                    values = listOf(
                        ExperimentSetValue("Test value"),
                        ExperimentSetValue("\uD83D\uDE00"),
                        ExperimentSetValue("\uD83E\uDEE0"),
                        ExperimentSetValue("\uD83E\uDEE8")
                    )
                )
                createExperiment(
                    ExperimentIO(name = "Sample experiment", setUids = listOf(newSet.uid)),
                    set = newSet
                )
                createSet(newSet)
            }
        }
    }

    fun toggleExperiment(experimentUid: String, play: Boolean) {
        viewModelScope.launch {
            val experiment = dataManager.experiments.value.first { it.data.uid == experimentUid }

            dataManager.activeExperiments.update { prev ->
                if (play) prev.plus(experiment.data.uid) else prev.minus(experiment.data.uid)
            }

            val starting = play && (experiment.data.displayFrequency is ExperimentIO.DisplayFrequency.BeginEnd
                    || experiment.data.displayFrequency is ExperimentIO.DisplayFrequency.Permanent)
            val ending = !play && experiment.data.displayFrequency is ExperimentIO.DisplayFrequency.BeginEnd
            val terminate = !play && experiment.data.displayFrequency is ExperimentIO.DisplayFrequency.Permanent

            if (starting || ending) {
                dataManager.experimentsToShow.update { prev ->
                    prev.plus(experiment).distinctBy { it.data.uid }
                }
            }
            if (terminate) {
                dataManager.experimentsToShow.update { prev ->
                    prev.filter { it.data.uid != experimentUid }
                }
            }
        }
    }

    private suspend fun getSets() {
        _sets.value = repository.getExperimentSets()
    }

    private suspend fun getExperiments() {
        dataManager.experiments.value = repository.getExperiments(matrixUserId).map { experiment ->
            FullExperiment(
                data = experiment,
                sets = _sets.value.filter { it.uid in experiment.setUids }
            )
        }
    }

    fun createExperiment(experiment: ExperimentIO, set: ExperimentSet? = null) {
        viewModelScope.launch {
            repository.insertExperiment(experiment)
            dataManager.experiments.update { prev ->
                prev.plus(
                    FullExperiment(
                        data = experiment,
                        sets = set?.let { listOf(it) } ?: listOf()
                    )
                )
            }
        }
    }

    fun createSet(set: ExperimentSet) {
        viewModelScope.launch {
            repository.insertSet(set)
            _sets.update {
                it.plus(set)
            }
        }
    }

    fun updateSet(set: ExperimentSet) {
        viewModelScope.launch {
            repository.insertSet(set)
            _sets.update {
                it.map { oldSet ->
                    if (oldSet.uid == set.uid) set else oldSet
                }
            }
        }
    }

    fun removeSet(uid: String) {
        viewModelScope.launch {
            repository.removeSet(uid)
            _sets.update { prev ->
                prev.toMutableList().apply {
                    fastIterateRemove { it.uid == uid }
                }
            }
        }
    }

    fun changeSetOf(experimentUid: String, setUid: String) {
        viewModelScope.launch {
            dataManager.experiments.update { prev ->
                prev.map { experiment ->
                    if (experiment.data.uid == experimentUid) {
                        experiment.copy(
                            data = experiment.data.copy(setUids = listOf(setUid)),
                            sets = listOf(sets.value.first { it.uid == setUid })
                        )
                    } else experiment
                }
            }
            repository.insertExperiment(
                dataManager.experiments.value.first { it.data.uid == experimentUid }.data.copy(
                    setUids = listOf(setUid)
                )
            )
        }
    }

    fun changeNameOf(experimentUid: String, name: CharSequence) {
        viewModelScope.launch {
            dataManager.experiments.update {
                it.map { experiment ->
                    if (experiment.data.uid == experimentUid) {
                        experiment.copy(data = experiment.data.copy(name = name.toString()))
                    } else experiment
                }
            }
            repository.insertExperiment(
                dataManager.experiments.value.first { it.data.uid == experimentUid }.data.copy(
                    name = name.toString()
                )
            )
        }
    }

    fun changeFrequencyOf(experimentUid: String, frequency: ExperimentIO.DisplayFrequency) {
        viewModelScope.launch {
            dataManager.experiments.update {
                it.map { experiment ->
                    if (experiment.data.uid == experimentUid) {
                        experiment.copy(data = experiment.data.copy(displayFrequency = frequency))
                    } else experiment
                }
            }
            repository.insertExperiment(
                dataManager.experiments.value.first { it.data.uid == experimentUid }.data.copy(
                    displayFrequency = frequency
                )
            )
        }
    }

    fun changeBehaviorOf(experimentUid: String, behavior: ExperimentIO.ChoiceBehavior) {
        viewModelScope.launch {
            dataManager.experiments.update {
                it.map { experiment ->
                    if (experiment.data.uid == experimentUid) {
                        experiment.copy(data = experiment.data.copy(choiceBehavior = behavior))
                    } else experiment
                }
            }
            repository.insertExperiment(
                dataManager.experiments.value.first { it.data.uid == experimentUid }.data.copy(
                    choiceBehavior = behavior
                )
            )
        }
    }

    fun deleteExperiment(uid: String) {
        viewModelScope.launch {
            repository.deleteExperiment(uid)
            dataManager.experiments.update {
                it.filter { experiment ->
                    experiment.data.uid != uid
                }
            }
        }
    }
}
