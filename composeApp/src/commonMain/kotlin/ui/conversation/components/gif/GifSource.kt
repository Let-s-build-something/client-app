package ui.conversation.components.gif

import androidx.paging.PagingSource
import androidx.paging.PagingState
import base.utils.orZero
import coil3.network.HttpException
import data.io.base.BaseResponse
import data.io.social.network.conversation.giphy.GiphyData
import data.io.social.network.conversation.giphy.GiphyPageResponse
import kotlinx.io.IOException

/** factory for making paging requests */
class GifSource(
    private val size: Int,
    private val getGifs: suspend (offset: Int, size: Int) -> BaseResponse<GiphyPageResponse>
): PagingSource<Int, GiphyData>() {

    override fun getRefreshKey(state: PagingState<Int, GiphyData>): Int? {
        return state.anchorPosition?.let {
            state.closestPageToPosition(it)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(it)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GiphyData> {
        return try {
            val response = getGifs(params.key.orZero(), size)
            val data = response.success?.data ?: return LoadResult.Error(
                Throwable(message = response.error?.errors?.firstOrNull())
            )

            val offsetTop = data.pagination?.offset?.plus(data.pagination.count.orZero()).orZero()

            LoadResult.Page(
                data = data.data.orEmpty(),
                prevKey = if(data.pagination?.offset.orZero() > 0) {
                    data.pagination?.offset?.minus(1)?.minus(size)?.plus(1)
                } else null,
                nextKey = if(offsetTop < data.pagination?.totalCount.orZero()) {
                    offsetTop
                } else null,
                itemsAfter = if(offsetTop < data.pagination?.totalCount.orZero()) {
                    size.coerceAtMost(data.pagination?.totalCount.orZero() - offsetTop)
                }else 0,
                itemsBefore = if(data.pagination?.offset.orZero() > 0) {
                    data.pagination?.offset?.minus(1)?.minus(size).orZero().plus(1).coerceAtLeast(0)
                }else 0
            )
        } catch (exception: IOException) {
            return LoadResult.Error(exception)
        } catch (exception: HttpException) {
            return LoadResult.Error(exception)
        }
    }
}