package com.chattingapp.ui.chat.adapter


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R
import com.chattingapp.ui.chat.Message
import com.bumptech.glide.Glide

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
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {
    override fun getItemViewType(position: Int): Int {
        val m = getItem(position)
        val out = m.senderId == currentUserId
        return when (m.messageType) {
            "voice" -> if (out) TYPE_VOICE_OUT else TYPE_VOICE_IN
            "video" -> if (out) TYPE_VIDEO_OUT else TYPE_VIDEO_IN
            else -> if (out) TYPE_TEXT_OUT else TYPE_TEXT_IN
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
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
        val msg = getItem(position)
        when (holder) {
            is TextViewHolder -> holder.bind(msg)
            is VoiceViewHolder -> holder.bind(msg)
            is VideoViewHolder -> holder.bind(msg)
        }
    }

    inner class TextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvContent: TextView = view.findViewById(R.id.tvMessageText)
        private val tvTime: TextView = view.findViewById(R.id.tvMessageTime)


        fun bind(message: Message) {
            tvContent.text = message.content ?: ""
            tvTime.text = message.sentAt
        }
    }

    inner class VoiceViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val btnPlay: ImageButton = v.findViewById(R.id.btnPlayVoice)
        private val tvDuration: TextView = v.findViewById(R.id.tvVoiceDuration)
        private val tvTranscript: TextView = v.findViewById(R.id.tvTranscript)
        private val tvAction: TextView = v.findViewById(R.id.tvTranscriptAction)
        private val tvTime: TextView? = v.findViewById(R.id.tvMessageTime)

        fun bind(m: Message) {
            tvDuration.text = m.durationSec?.let { String.format("%02d:%02d", it / 60, it % 60) } ?: ""
            tvTime?.text = m.sentAt

            val hasTranscript = !m.transcriptText.isNullOrBlank() && !m.transcriptText.equals("null", true)
            if (hasTranscript) {
                tvTranscript.visibility = View.VISIBLE
                tvTranscript.text = m.transcriptText
                tvAction.visibility = View.GONE
            } else {
                tvTranscript.visibility = View.GONE
                tvAction.visibility = View.VISIBLE
                tvAction.setOnClickListener { onTranscribe(m) }
            }

            btnPlay.setOnClickListener { onPlayVoice(m, it) }
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
            tvTime?.text = message.sentAt
            btnPlay?.setOnClickListener {
                // handle play video
            }
        }
    }
}