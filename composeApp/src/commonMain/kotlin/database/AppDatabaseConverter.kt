package database

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import data.io.experiment.ExperimentIO
import data.io.experiment.ExperimentSetValue
import data.io.matrix.room.RoomSummary
import data.io.social.network.conversation.message.ConversationMessageIO.VerificationRequestInfo
import data.io.social.network.conversation.message.MediaIO
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.serialization.json.Json
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.UnreadNotificationCounts
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.StrippedState
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Invite
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import org.koin.mp.KoinPlatform
import ui.conversation.components.experimental.gravity.GravityData

/** Factory converter for Room database */
@ProvidedTypeConverter
class AppDatabaseConverter {

    private val json: Json by KoinPlatform.getKoin().inject()

    /** Converts long to a string */
    @TypeConverter
    fun fromLongList(value: List<Long>?): String? {
        return value?.joinToString(",")
    }
    /** Converts string to an integer long */
    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        return value?.split(",")?.mapNotNull { it.toLongOrNull() }    }
    
    @TypeConverter
    fun fromRoomSummary(value: RoomSummary): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toRoomSummary(value: String): RoomSummary {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun fromVerificationRequestInfo(value: VerificationRequestInfo): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toVerificationRequestInfo(value: String): VerificationRequestInfo {
        return json.decodeFromString(value)
    }
    
    @TypeConverter
    fun fromPresenceEventContent(value: PresenceEventContent): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toPresenceEventContent(value: String): PresenceEventContent {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun fromGravityData(value: GravityData): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toGravityData(value: String): GravityData {
        return json.decodeFromString(value)
    }
    
    @TypeConverter
    fun fromEncryptionAlgorithm(value: EncryptionAlgorithm): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toInvite(value: String): Invite {
        return json.decodeFromString(value)
    }
    @TypeConverter
    fun fromInvite(value: Invite): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toEncryptionAlgorithm(value: String): EncryptionAlgorithm {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun fromUserId(value: UserId): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toUserId(value: String): UserId {
        return json.decodeFromString(value)
    }
    
    @TypeConverter
    fun fromStrippedState(value: StrippedState?): String? {
        return if(value == null) null else json.encodeToString(value)
    }
    
    @TypeConverter
    fun toStrippedState(value: String?): StrippedState? {
        return if(value == null) null else json.decodeFromString(value)
    }

    @TypeConverter
    fun fromUnreadNotificationCounts(value: UnreadNotificationCounts?): String? {
        return if(value == null) null else json.encodeToString(value)
    }

    @TypeConverter
    fun toUnreadNotificationCounts(value: String?): UnreadNotificationCounts? {
        return if(value == null) null else json.decodeFromString(value)
    }

    @TypeConverter
    fun fromMediaIO(value: MediaIO?): String? {
        return if(value == null) null else json.encodeToString(value)
    }

    @TypeConverter
    fun toMediaIO(value: String?): MediaIO? {
        return if(value == null) null else json.decodeFromString(value)
    }
    
    @TypeConverter
    fun fromDisplayFrequency(value: ExperimentIO.DisplayFrequency?): String? {
        return if(value == null) null else json.encodeToString(value)
    }

    @TypeConverter
    fun toDisplayFrequency(value: String?): ExperimentIO.DisplayFrequency? {
        return if(value == null) null else json.decodeFromString(value)
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return if (value.isEmpty()) "" else value.joinToString(",")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return value.split(",")
    }

    @TypeConverter
    fun fromExperimentSetValueList(value: List<ExperimentSetValue>): String {
        return if (value.isEmpty()) "" else json.encodeToString(value)
    }

    @TypeConverter
    fun toExperimentSetValueList(value: String): List<ExperimentSetValue> {
        return if (value.isEmpty()) emptyList() else json.decodeFromString(value)
    }

    @TypeConverter
    fun fromActiveSensorList(value: List<ExperimentIO.ActiveSensor>): String {
        return if (value.isEmpty()) "" else json.encodeToString(value)
    }

    @TypeConverter
    fun toActiveSensorList(value: String): List<ExperimentIO.ActiveSensor> {
        return if (value.isEmpty()) emptyList() else json.decodeFromString(value)
    }

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime): String {
        return value.format(LocalDateTime.Formats.ISO)
    }
    
    @TypeConverter
    fun toLocalDateTime(value: String): LocalDateTime {
        return LocalDateTime.parse(value, LocalDateTime.Formats.ISO)
    }
}
