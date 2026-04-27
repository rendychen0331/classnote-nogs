package com.rendy.classnote.ui.classrecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.data.local.entity.ClassRecordMediaEntity
import com.rendy.classnote.data.repository.ClassRecordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClassRecordViewModel(
    private val repository: ClassRecordRepository
) : ViewModel() {

    val records: StateFlow<List<ClassRecordEntity>> = repository.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getById(id: Long): ClassRecordEntity? = repository.getById(id)

    suspend fun save(record: ClassRecordEntity, newMediaItems: List<ClassRecordMediaEntity>): Long {
        val savedId = if (record.id > 0) {
            repository.update(record)
            record.id
        } else {
            repository.insert(record)
        }
        newMediaItems.forEach { media ->
            repository.insertMedia(media.copy(recordId = savedId))
        }
        return savedId
    }

    suspend fun getMediaOnce(recordId: Long) = repository.getMediaForRecordOnce(recordId)

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteAllMediaForRecord(id)
            repository.deleteById(id)
        }
    }

    class Factory(private val repository: ClassRecordRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ClassRecordViewModel(repository) as T
    }
}
