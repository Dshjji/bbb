package com.instructure.student.di

import com.instructure.canvasapi2.managers.CalendarEventManager
import com.instructure.canvasapi2.managers.CourseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    fun provideCourseManager(): CourseManager {
        return CourseManager
    }

    @Provides
    fun providesCalendarEventManager(): CalendarEventManager {
        return CalendarEventManager
    }

}