package com.chattingapp.ui.chat.adapter


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R
import com.chattingapp.ui.chat.ChatItem
import com.chattingapp.ui.chat.Message
import com.chattingapp.ui.chat.WaveformView
import com.chattingapp.ui.chat.ChatRoomActivity
import com.bumptech.glide.Glide

private const val TYPE_DATE_HEADER = 0
private const val TYPE_TEXT_IN = 1
private const val TYPE_TEXT_OUT = 2
private const val TYPE_VOICE_IN = 3
private const val TYPE_VOICE_OUT = 4
private const val TYPE_VIDEO_IN = 5
private const val TYPE_VIDEO_OUT = 6

class MessageAdapter(
    private val currentUserId: String,
    private val onPlayVoice: (Message, View) -> Unit,
    private val onTranscribe: (Message) -> Unit
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatItemDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatItem.DateHeader -> TYPE_DATE_HEADER
            is ChatItem.MessageItem -> {
                val m = item.message
                val out = m.senderId == currentUserId
                when (m.messageType) {
                    "voice" -> if (out) TYPE_VOICE_OUT else TYPE_VOICE_IN
                    "video" -> if (out) TYPE_VIDEO_OUT else TYPE_VIDEO_IN
                    else -> if (out) TYPE_TEXT_OUT else TYPE_TEXT_IN
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DATE_HEADER -> DateHeaderViewHolder(inflater.inflate(R.layout.item_date_header, parent, false))
            TYPE_TEXT_IN -> TextViewHolder(inflater.inflate(R.layout.item_message_text_incoming, parent, false))
            TYPE_TEXT_OUT -> TextViewHolder(inflater.inflate(R.layout.item_message_text_outgoing, parent, false))
            TYPE_VOICE_IN -> VoiceViewHolder(inflater.inflate(R.layout.item_message_voice_incoming, parent, false))
            TYPE_VOICE_OUT -> VoiceViewHolder(inflater.inflate(R.layout.item_message_voice_outgoing, parent, false))
            TYPE_VIDEO_IN -> VideoViewHolder(inflater.inflate(R.layout.item_message_video_incoming, parent, false))
            TYPE_VIDEO_OUT -> VideoViewHolder(inflater.inflate(R.layout.item_message_video_outgoing, parent, false))
            else -> TextViewHolder(inflater.inflate(R.layout.item_message_text_incoming, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item)
            is ChatItem.MessageItem -> {
                when (holder) {
                    is TextViewHolder -> holder.bind(item.message)
                    is VoiceViewHolder -> holder.bind(item.message)
                    is VideoViewHolder -> holder.bind(item.message)
                }
            }
        }
    }

    inner class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate: TextView = view.findViewById(R.id.tvDateHeader)

        fun bind(header: ChatItem.DateHeader) {
            tvDate.text = header.date
        }
    }

    inner class TextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvContent: TextView = view.findViewById(R.id.tvMessageText)
        private val tvTime: TextView = view.findViewById(R.id.tvMessageTime)

        fun bind(message: Message) {
            tvContent.text = message.content ?: ""
            tvTime.text = message.sentAt // Only show time (HH:mm)
        }
    }

    inner class VoiceViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val btnPlay: ImageButton = v.findViewById(R.id.btnPlayVoice)
        private val tvDuration: TextView = v.findViewById(R.id.tvVoiceDuration)
        private val tvTranscript: TextView = v.findViewById(R.id.tvTranscript)
        private val tvAction: TextView = v.findViewById(R.id.tvTranscriptAction)
        private val tvTime: TextView? = v.findViewById(R.id.tvMessageTime)
        private val waveformView: WaveformView? = v.findViewById(R.id.waveformView)

        fun bind(m: Message) {
            val totalDuration = m.durationSec ?: 0
            tvDuration.text = formatDuration(totalDuration)
            tvTime?.text = m.sentAt // Only show time (HH:mm)

            val isIncoming = m.senderId != currentUserId
            if (isIncoming) {
                waveformView?.setWaveColor(ContextCompat.getColor(itemView.context, android.R.color.black))
            } else {
                waveformView?.setWaveColor(ContextCompat.getColor(itemView.context, android.R.color.white))
            }

            val hasTranscript = !m.transcriptText.isNullOrBlank() && !m.transcriptText.equals("null", true)

            when {
                hasTranscript -> {
                    tvTranscript.visibility = View.VISIBLE
                    tvTranscript.text = m.transcriptText
                    tvAction.visibility = View.GONE
                }
                m.isTranscribing -> {
                    tvTranscript.visibility = View.GONE
                    tvAction.visibility = View.VISIBLE
                    tvAction.text = "Sedang proses..."
                    tvAction.isEnabled = false
                    tvAction.alpha = 0.5f
                    tvAction.setOnClickListener(null)
                }
                else -> {
                    tvTranscript.visibility = View.GONE
                    tvAction.visibility = View.VISIBLE
                    tvAction.text = "Transkrip"
                    tvAction.isEnabled = true
                    tvAction.alpha = 1.0f
                    tvAction.setOnClickListener {
                        m.isTranscribing = true
                        notifyItemChanged(adapterPosition)
                        onTranscribe(m)
                    }
                }
            }

            btnPlay.setOnClickListener {
                m.mediaUrl?.let { url ->
                    val playerHelper = (itemView.context as? ChatRoomActivity)?.getPlayerHelper()

                    if (playerHelper?.isPlaying(url) == true) {
                        playerHelper.stop()
                        btnPlay.setImageResource(R.drawable.ic_play)
                        waveformView?.stopAnimation()
                        tvDuration.text = formatDuration(totalDuration)
                    } else {
                        btnPlay.setImageResource(R.drawable.ic_pause)
                        waveformView?.startAnimation()

                        playerHelper?.play(
                            url = url,
                            onProgress = { progress, currentMs, _ ->
                                val currentSec = currentMs / 1000
                                tvDuration.text = formatDuration(currentSec)
                                waveformView?.updateProgress(progress)
                            },
                            onComplete = {
                                btnPlay.setImageResource(R.drawable.ic_play)
                                waveformView?.stopAnimation()
                                tvDuration.text = formatDuration(totalDuration)
                            }
                        )
                    }
                }
            }
        }

        private fun formatDuration(seconds: Int): String {
            return String.format("%02d:%02d", seconds / 60, seconds % 60)
        }
    }

    inner class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivThumbnail: ImageView = view.findViewById(R.id.ivVideoThumb)
        private val tvDuration: TextView = view.findViewById(R.id.tvVideoDuration)
        private val tvTime: TextView? = view.findViewById(R.id.tvMessageTime)
        private val btnPlay: ImageButton? = view.findViewById(R.id.btnPlayVideo)

        fun bind(message: Message) {
            Glide.with(itemView.context)
                .load(message.mediaUrl)
                .placeholder(R.drawable.ic_video)
                .into(ivThumbnail)

            tvDuration.text = message.durationSec?.let { String.format("%02d:%02d", it / 60, it % 60) } ?: ""
            tvTime?.text = message.sentAt // Only show time (HH:mm)
            btnPlay?.setOnClickListener {
                // handle play video
            }
        }
    }
}
