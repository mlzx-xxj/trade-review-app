package com.tradereview.app.data

import androidx.room.TypeConverter
import com.tradereview.app.domain.ProfessionalJson

class ProfessionalConverters {
    @TypeConverter fun encodeProfessional(value: ProfessionalTrade?): String? = value?.let { ProfessionalJson.encode(ProfessionalJson.professional(it)) }
    @TypeConverter fun decodeProfessional(value: String?): ProfessionalTrade? = value?.let { ProfessionalJson.parseProfessional(ProfessionalJson.decode(it)) }
    @TypeConverter fun encodeDraft(value: ProfessionalDraft?): String? = value?.let { ProfessionalJson.encode(ProfessionalJson.draft(it)) }
    @TypeConverter fun decodeDraft(value: String?): ProfessionalDraft? = value?.let { ProfessionalJson.parseDraft(ProfessionalJson.decode(it)) }
    @TypeConverter fun encodeRecord(value: RiskRecord?): String? = value?.let { ProfessionalJson.encode(ProfessionalJson.record(it)) }
    @TypeConverter fun decodeRecord(value: String?): RiskRecord? = value?.let { ProfessionalJson.parseRecord(ProfessionalJson.decode(it)) }
}
