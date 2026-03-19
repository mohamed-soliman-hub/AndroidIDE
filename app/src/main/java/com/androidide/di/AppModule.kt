package com.androidide.di

import android.content.Context
import com.androidide.core.ai.AIService
import com.androidide.core.build.DependencyResolver
import com.androidide.core.build.GitHubActionsService
import com.androidide.core.build.KotlinCompilerEngine
import com.androidide.core.build.OnDeviceBuildEngine
import com.androidide.core.build.GradleManager
import com.androidide.core.editor.CodeCompletionEngine
import com.androidide.core.editor.SyntaxHighlighter
import com.androidide.core.git.GitManager
import com.androidide.core.project.ProjectManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideProjectManager(@ApplicationContext ctx: Context): ProjectManager = ProjectManager(ctx)

    @Provides @Singleton
    fun provideGradleManager(@ApplicationContext ctx: Context): GradleManager = GradleManager(ctx)

    @Provides @Singleton
    fun provideGitManager(): GitManager = GitManager()

    @Provides @Singleton
    fun provideCodeCompletionEngine(): CodeCompletionEngine = CodeCompletionEngine()

    @Provides @Singleton
    fun provideSyntaxHighlighter(): SyntaxHighlighter = SyntaxHighlighter()

    @Provides @Singleton
    fun provideAIService(): AIService = AIService()

    @Provides @Singleton
    fun provideDependencyResolver(@ApplicationContext ctx: Context): DependencyResolver = DependencyResolver(ctx)

    @Provides @Singleton
    fun provideGitHubActionsService(@ApplicationContext ctx: Context): GitHubActionsService = GitHubActionsService(ctx)

    @Provides @Singleton
    fun provideKotlinCompilerEngine(@ApplicationContext ctx: Context): KotlinCompilerEngine = KotlinCompilerEngine(ctx)

    @Provides @Singleton
    fun provideOnDeviceBuildEngine(
        @ApplicationContext ctx: Context,
        kotlinCompiler: KotlinCompilerEngine
    ): OnDeviceBuildEngine = OnDeviceBuildEngine(ctx, kotlinCompiler)
}
