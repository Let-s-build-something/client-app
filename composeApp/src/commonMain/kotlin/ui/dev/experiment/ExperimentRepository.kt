package ui.dev.experiment

import data.io.experiment.ExperimentIO
import data.io.experiment.ExperimentSet
import database.dao.experiment.ExperimentDao
import database.dao.experiment.ExperimentSetDao
import ui.dev.DeveloperConsoleRepository

class ExperimentRepository(
    private val experimentDao: ExperimentDao,
    private val experimentSetDao: ExperimentSetDao
): DeveloperConsoleRepository() {
    suspend fun insertSet(set: ExperimentSet) = experimentSetDao.insert(set)
    suspend fun removeSet(uid: String) = experimentSetDao.remove(uid)
    suspend fun getExperiments(owner: String?) = experimentDao.getAll(owner)
    suspend fun insertExperiment(experiment: ExperimentIO) = experimentDao.insert(experiment)
    suspend fun getExperimentSets() = experimentSetDao.getAll()
    suspend fun deleteExperiment(uid: String) = experimentDao.remove(uid)
}